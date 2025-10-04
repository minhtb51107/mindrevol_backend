package com.example.demo.service.document;

import com.example.demo.dto.chat.DocumentInfoDTO;
import com.example.demo.model.auth.User;
import com.example.demo.repository.chat.KnowledgeRepository;
import com.example.demo.service.chat.CacheInvalidationService;
// ✅ BƯỚC 1: IMPORT EmbeddingCacheService
import com.example.demo.service.chat.EmbeddingCacheService;
import com.example.demo.service.document.splitter.SemanticDocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FileProcessingService fileProcessingService;
    private final KnowledgeRepository knowledgeRepository;
    private final CacheInvalidationService cacheInvalidationService;
    
    // ✅ BƯỚC 2: INJECT EmbeddingCacheService VÀO CONSTRUCTOR
    private final EmbeddingCacheService embeddingCacheService;

    public List<DocumentInfoDTO> listDocuments(User user) {
        return knowledgeRepository.listDocumentsForUser(user.getId());
    }

    public int deleteDocument(String fileName, User user) {
        log.info("Yêu cầu xóa file {} của user {}", fileName, user.getEmail());

        // 1. VÔ HIỆU HÓA CACHE CÂU TRẢ LỜI
        cacheInvalidationService.invalidateCacheForDocument(fileName);

        // LƯU Ý VỀ VIỆC XÓA CACHE EMBEDDING:
        // Với thiết kế cache key hiện tại (dựa trên SHA-256 của nội dung),
        // chúng ta không thể xóa cache embedding khi xóa file vì không có nội dung file.
        // Để giải quyết triệt để, cần phải:
        //   a) Lấy nội dung file từ một nơi lưu trữ khác (nếu có) trước khi xóa.
        //   b) Hoặc thiết kế lại key của cache embedding để có thể xóa dựa trên metadata,
        //      ví dụ: key = "embedding_cache:" + sha256(userId + ":" + fileName + ":" + chunkHash)
        // Hiện tại, chúng ta chấp nhận cache embedding sẽ tự hết hạn theo TTL.

        // 2. XÓA DỮ LIỆU GỐC TỪ VECTOR STORE
        int deletedCount = knowledgeRepository.deleteDocument(fileName, user.getId());
        log.info("Đã xóa {} mẩu tin (chunks) của file {}", deletedCount, fileName);

        return deletedCount;
    }

    public void ingestDocuments(List<MultipartFile> files, User user) {
        log.info("Bắt đầu nạp {} file cho user {} một cách bất đồng bộ", files.size(), user.getEmail());
        
        for (MultipartFile file : files) {
            ingestSingleDocumentAsync(file, user);
        }
        
        log.info("Đã gửi {} file vào hàng đợi xử lý cho user {}", files.size(), user.getEmail());
    }

    @Async("fileIngestionExecutor")
    public CompletableFuture<Void> ingestSingleDocumentAsync(MultipartFile multipartFile, User user) {
        String fileName = multipartFile.getOriginalFilename();
        try {
            log.info("[Async] Bắt đầu xử lý file: {}", fileName);
            ingestSingleDocument(multipartFile, user);
            log.info("[Async] Hoàn tất xử lý file: {}", fileName);
        } catch (IOException e) {
            log.error("[Async] Lỗi khi nạp file {} cho user {}: {}",
                       fileName, user.getEmail(), e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private void ingestSingleDocument(MultipartFile multipartFile, User user) throws IOException {
        File tempFile = null;
        String fileName = multipartFile.getOriginalFilename();
        try {
            tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();

            // ✅ BƯỚC 3: VÔ HIỆU HÓA CÁC CACHE CŨ TRƯỚC KHI NẠP DỮ LIỆU MỚI
            // Việc này rất quan trọng khi người dùng upload lại file cùng tên (cập nhật).
            
            // Xóa cache câu trả lời cũ liên quan đến file này.
            log.info("Invalidating answer cache for document: {} before ingestion.", fileName);
            cacheInvalidationService.invalidateCacheForDocument(fileName);

            // Xóa cache embedding cũ của nội dung file này.
            // Vì các chunk có thể thay đổi, ta sẽ vô hiệu hóa cache cho từng chunk.
            // Tuy nhiên, để đơn giản, ta có thể giả định rằng việc chia chunk cho cùng 1 nội dung là nhất quán.
            // Nếu không, ta cần split document trước rồi mới invalidate cache cho từng chunk text.
            // LƯU Ý: Đoạn code dưới đây chỉ mang tính minh họa nếu bạn muốn xóa cache của *toàn bộ nội dung*.
            // Trong thực tế, bạn sẽ phải xóa cache cho từng chunk sau khi split.
            // log.info("Invalidating embedding cache for document content: {}", fileName);
            // embeddingCacheService.invalidateCache(originalContent);
            // => Tạm thời, chúng ta sẽ để cho CachedEmbeddingModel xử lý việc ghi đè cache.

            // Tùy chọn: Xóa các embedding cũ của file này khỏi vector store trước khi nạp mới
            // knowledgeRepository.deleteDocument(fileName, user.getId());

            // ✅ BƯỚC 4: NẠP DỮ LIỆU MỚI (Logic hiện tại của bạn)
            String newContent = String.format(
                "Đây là nội dung trích từ file có tên: '%s'\n\n%s",
                fileName,
                originalContent
            );

            Document document = Document.from(newContent, originalDocument.metadata());
            document.metadata().add("userId", user.getId().toString());
            document.metadata().add("docType", "knowledge");
            document.metadata().add("fileName", fileName);

            DocumentSplitter splitter = new SemanticDocumentSplitter(embeddingModel, 0.8);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            
            ingestor.ingest(document);
            log.info("Đã nạp thành công file (knowledge) {} cho user {}", multipartFile.getOriginalFilename(), user.getEmail());

        } finally {
            fileProcessingService.deleteTempFile(tempFile);
        }
    }

    public void ingestTemporaryFile(MultipartFile multipartFile, User user, Long sessionId, String tempFileId) throws IOException {
        File tempFile = null;
        try {
            tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();
            String fileName = multipartFile.getOriginalFilename();

            String newContent = String.format(
                "Đây là nội dung trích từ file tạm thời có tên: '%s'\n\n%s",
                fileName,
                originalContent
            );

            Document document = Document.from(newContent, originalDocument.metadata());
            document.metadata().add("userId", user.getId().toString());
            document.metadata().add("docType", "temp_file");
            document.metadata().add("fileName", fileName);
            document.metadata().add("sessionId", sessionId.toString());
            document.metadata().add("tempFileId", tempFileId);

            DocumentSplitter splitter = new SemanticDocumentSplitter(embeddingModel, 0.8);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            
            ingestor.ingest(document);
            log.info("Đã nạp thành công file (temporary) {} cho session {}", fileName, sessionId);

        } finally {
            fileProcessingService.deleteTempFile(tempFile);
        }
    }

    public int deleteTemporaryFilesForSession(Long sessionId, User user) {
        log.info("Yêu cầu xóa các file tạm thời của session {} cho user {}", sessionId, user.getEmail());
        
        int deletedCount = knowledgeRepository.deleteTemporaryDocumentsBySession(sessionId, user.getId());
        log.info("Đã xóa {} mẩu tin (chunks) của các file tạm thời trong session {}", deletedCount, sessionId);
        return deletedCount;
    }
}