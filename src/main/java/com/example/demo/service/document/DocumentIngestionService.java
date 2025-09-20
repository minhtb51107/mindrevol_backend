package com.example.demo.service.document;

import com.example.demo.dto.chat.DocumentInfoDTO;
import com.example.demo.model.auth.User;
import com.example.demo.repository.chat.KnowledgeRepository;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    // Inject các thành phần trừu tượng đã tồn tại
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FileProcessingService fileProcessingService; // ✅ THÊM MỚI
    private final KnowledgeRepository knowledgeRepository; // ✅ THÊM MỚI   
    /**
     * ✅ HÀM MỚI: Lấy danh sách file trong kho tri thức
     */
    public List<DocumentInfoDTO> listDocuments(User user) {
        return knowledgeRepository.listDocumentsForUser(user.getId());
    }

    /**
     * ✅ HÀM MỚI: Xóa một file khỏi kho tri thức
     */
    public int deleteDocument(String fileName, User user) {
        log.info("Yêu cầu xóa file {} của user {}", fileName, user.getEmail());
        int deletedCount = knowledgeRepository.deleteDocument(fileName, user.getId());
        log.info("Đã xóa {} mẩu tin (chunks) của file {}", deletedCount, fileName);
        return deletedCount;
    }

    // 🔥 HÀM NÀY BỊ XÓA (đã được thay bằng ingestSingleDocument)
    // public void ingestDocument(MultipartFile multipartFile, User user) throws IOException { ... }
    
    /**
     * ✅ HÀM MỚI: Nhận một danh sách file và xử lý tuần tự
     */
    public void ingestDocuments(List<MultipartFile> files, User user) {
        log.info("Bắt đầu nạp {} file cho user {}", files.size(), user.getEmail());
        
        for (MultipartFile file : files) {
            try {
                // Gọi hàm xử lý 1 file (đã đổi tên)
                this.ingestSingleDocument(file, user);
            } catch (Exception e) {
                // Ghi log lỗi cho file cụ thể nhưng vẫn tiếp tục với các file khác
                log.error("Lỗi khi nạp file {} cho user {}: {}", 
                           file.getOriginalFilename(), user.getEmail(), e.getMessage());
            }
        }
        log.info("Hoàn tất nạp {} file cho user {}", files.size(), user.getEmail());
    }

    /**
     * Nạp file vào KHO TRI THỨC LÂU DÀI (docType = 'knowledge')
     */
    public void ingestSingleDocument(MultipartFile multipartFile, User user) throws IOException {
        
        File tempFile = null;
        
        try {
        	tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            
            // 1. Tải document gốc
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();
            String fileName = multipartFile.getOriginalFilename();

            // 2. TẠO NỘI DUNG MỚI: Thêm tên file vào đầu văn bản
            String newContent = String.format(
                "Đây là nội dung trích từ file có tên: '%s'\n\n%s",
                fileName,
                originalContent
            );

            // 3. Tạo document mới với nội dung đã sửa đổi
            Document document = Document.from(newContent, originalDocument.metadata());
            
            // 4. Thêm metadata
            document.metadata().add("userId", user.getId().toString());
            document.metadata().add("docType", "knowledge"); // ✅ <--- TRI THỨC LÂU DÀI
            document.metadata().add("fileName", fileName);

            // 5. Nạp
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            
            ingestor.ingest(document);
            log.info("Đã nạp thành công file (knowledge) {} cho user {}", multipartFile.getOriginalFilename(), user.getEmail());

        } finally {
            fileProcessingService.deleteTempFile(tempFile);
        }
    }

    /**
     * ✅ HÀM MỚI: Nạp file TẠM THỜI cho RAG (docType = 'temp_file')
     * Chỉ tồn tại trong phạm vi của cuộc trò chuyện.
     */
    public void ingestTemporaryFile(MultipartFile multipartFile, User user, Long sessionId, String tempFileId) throws IOException {
        
        File tempFile = null;
        
        try {
        	tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            
            // 1. Tải document gốc
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();
            String fileName = multipartFile.getOriginalFilename();

            // 2. TẠO NỘI DUNG MỚI: Thêm tên file vào đầu văn bản
            String newContent = String.format(
                "Đây là nội dung trích từ file tạm thời có tên: '%s'\n\n%s",
                fileName,
                originalContent
            );

            // 3. Tạo document mới với nội dung đã sửa đổi
            Document document = Document.from(newContent, originalDocument.metadata());
            
            // 4. Thêm metadata TẠM THỜI
            document.metadata().add("userId", user.getId().toString()); // Vẫn cần để phân quyền
            document.metadata().add("docType", "temp_file");    // ✅ <--- LOẠI FILE TẠM THỜI
            document.metadata().add("fileName", fileName);
            document.metadata().add("sessionId", sessionId.toString()); // ✅ Gắn vào session
            document.metadata().add("tempFileId", tempFileId); // ✅ Gắn ID file duy nhất

            // 5. Nạp
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            
            ingestor.ingest(document);
            log.info("Đã nạp thành công file (temporary) {} cho session {}", fileName, sessionId);

        } finally {
            fileProcessingService.deleteTempFile(tempFile);
        }
    }


    // Helper để chuyển MultipartFile sang File (bị lặp, có thể xóa nếu FileProcessingService được inject)
    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = File.createTempFile(file.getOriginalFilename(), ".tmp");
        try (FileOutputStream fos = new FileOutputStream(convFile);
             InputStream is = file.getInputStream()) {
            is.transferTo(fos);
        }
        return convFile;
    }
}