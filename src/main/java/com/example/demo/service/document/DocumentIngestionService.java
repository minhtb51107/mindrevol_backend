package com.example.demo.service.document;

import com.example.demo.dto.chat.DocumentInfoDTO;
import com.example.demo.model.auth.User;
import com.example.demo.repository.chat.KnowledgeRepository;
// ✅ THAY ĐỔI: Đảm bảo bạn đã tạo file SemanticDocumentSplitter.java trong package này hoặc package con
import com.example.demo.service.document.splitter.SemanticDocumentSplitter; 

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter; // Import DocumentSplitter
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

    public List<DocumentInfoDTO> listDocuments(User user) {
        return knowledgeRepository.listDocumentsForUser(user.getId());
    }

    public int deleteDocument(String fileName, User user) {
        log.info("Yêu cầu xóa file {} của user {}", fileName, user.getEmail());
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
        try {
            tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            
            Document originalDocument = fileProcessingService.loadDocument(tempFile);
            String originalContent = originalDocument.text();
            String fileName = multipartFile.getOriginalFilename();

            String newContent = String.format(
                "Đây là nội dung trích từ file có tên: '%s'\n\n%s",
                fileName,
                originalContent
            );

            Document document = Document.from(newContent, originalDocument.metadata());

            document.metadata().add("userId", user.getId().toString());
            document.metadata().add("docType", "knowledge");
            document.metadata().add("fileName", fileName);

            // ✅ THAY ĐỔI: Sử dụng SemanticDocumentSplitter thay vì recursive splitter.
            // Ngưỡng 0.8 là một giá trị khởi đầu tốt, bạn có thể tinh chỉnh sau.
            DocumentSplitter splitter = new SemanticDocumentSplitter(embeddingModel, 0.8);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter) // Sử dụng splitter mới
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

            // ✅ THAY ĐỔI: Áp dụng SemanticDocumentSplitter cho cả file tạm thời.
            DocumentSplitter splitter = new SemanticDocumentSplitter(embeddingModel, 0.8);

            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter) // Sử dụng splitter mới
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

    // Bạn có thể xóa hàm helper này nếu FileProcessingService đã xử lý tốt việc này.
    // private File convertMultiPartToFile(MultipartFile file) throws IOException { ... }
}