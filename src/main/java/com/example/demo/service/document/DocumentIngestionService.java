package com.example.demo.service.document;

import com.example.demo.dto.chat.DocumentInfoDTO;
import com.example.demo.model.auth.User;
import com.example.demo.repository.chat.KnowledgeRepository;
import com.example.demo.service.document.splitter.SemanticDocumentSplitter; 
// ✅ THÊM IMPORT
import com.example.demo.service.chat.CacheInvalidationService; 

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
    // ✅ TIÊM SERVICE VÔ HIỆU HÓA CACHE
    private final CacheInvalidationService cacheInvalidationService;

    public List<DocumentInfoDTO> listDocuments(User user) {
        return knowledgeRepository.listDocumentsForUser(user.getId());
    }

    public int deleteDocument(String fileName, User user) {
        log.info("Yêu cầu xóa file {} của user {}", fileName, user.getEmail());
        
        // 1. VÔ HIỆU HÓA CACHE TRƯỚC KHI XÓA DỮ LIỆU GỐC
        cacheInvalidationService.invalidateCacheForDocument(fileName);

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
            // ✅ BƯỚC 1: VÔ HIỆU HÓA CACHE CŨ
            // Logic này xử lý cho trường hợp người dùng upload lại file cùng tên (cập nhật).
            // Nó sẽ xóa tất cả các câu trả lời cũ trong cache liên quan đến file này.
            log.info("Invalidating cache for document: {} before ingestion.", fileName);
            cacheInvalidationService.invalidateCacheForDocument(fileName);

            // Tùy chọn: Xóa các embedding cũ của file này khỏi vector store trước khi nạp mới
            // knowledgeRepository.deleteDocument(fileName, user.getId());

            // ✅ BƯỚC 2: NẠP DỮ LIỆU MỚI (Logic hiện tại của bạn)
            tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();

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
        // ... Logic này có thể giữ nguyên vì file tạm thời thường không cần vô hiệu hóa cache phức tạp
        // vì chúng sẽ bị xóa theo session.
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
        
        // Mặc dù không bắt buộc, nhưng để đảm bảo sạch sẽ, bạn cũng có thể
        // vô hiệu hóa cache cho từng file tạm thời trước khi xóa.
        // Tuy nhiên, việc này đòi hỏi bạn phải lấy danh sách tên file trước khi xóa.
        // Cách đơn giản hơn là chấp nhận cache sẽ tự hết hạn (TTL).
        
        int deletedCount = knowledgeRepository.deleteTemporaryDocumentsBySession(sessionId, user.getId());
        log.info("Đã xóa {} mẩu tin (chunks) của các file tạm thời trong session {}", deletedCount, sessionId);
        return deletedCount;
    }
}