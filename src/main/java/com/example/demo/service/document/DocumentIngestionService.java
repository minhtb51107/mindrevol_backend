package com.example.demo.service.document;

import com.example.demo.model.auth.User;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    // Inject các thành phần trừu tượng đã tồn tại
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final FileProcessingService fileProcessingService; // ✅ THÊM MỚI

    public void ingestDocument(MultipartFile multipartFile, User user) throws IOException {
        
        // DocumentParser parser = new ApacheTikaDocumentParser(); // Xóa
        File tempFile = null;
        
        try {
            // ✅ SỬ DỤNG SERVICE MỚI
            tempFile = fileProcessingService.convertMultiPartToFile(multipartFile);
            Document document = fileProcessingService.loadDocument(tempFile);

            // 3. TRANSFORM STEP
            document.metadata().add("userId", user.getId().toString());
            document.metadata().add("docType", "knowledge");
            document.metadata().add("fileName", multipartFile.getOriginalFilename());

            // 4. CHUNKING/SPLITTING STEP
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 100))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();

            // 5. EMBED & WRITE STEPS
            ingestor.ingest(document);
            log.info("Đã nạp thành công file {} cho user {}", multipartFile.getOriginalFilename(), user.getEmail());

        } finally {
            // ✅ SỬ DỤNG SERVICE MỚI
            fileProcessingService.deleteTempFile(tempFile);
        }
    }

    // Helper để chuyển MultipartFile sang File
    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = File.createTempFile(file.getOriginalFilename(), ".tmp");
        try (FileOutputStream fos = new FileOutputStream(convFile);
             InputStream is = file.getInputStream()) {
            is.transferTo(fos);
        }
        return convFile;
    }
}