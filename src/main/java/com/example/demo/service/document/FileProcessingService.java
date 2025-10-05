package com.example.demo.service.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class FileProcessingService {

    private final DocumentParser parser;

    public FileProcessingService() {
        // Khởi tạo Tika parser một lần
        this.parser = new ApacheTikaDocumentParser();
    }

    /**
     * Chuyển MultipartFile (từ web) thành File tạm
     */
    public File convertMultiPartToFile(MultipartFile file) throws IOException {
        // Đảm bảo tên file không chứa ký tự path (security)
        String safeFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        File convFile = File.createTempFile(safeFileName, ".tmp");
        
        try (FileOutputStream fos = new FileOutputStream(convFile);
             InputStream is = file.getInputStream()) {
            is.transferTo(fos);
        }
        return convFile;
    }

    /**
     * Đọc file (PDF, DOCX, TXT) và trả về Document của LangChain4j
     */
    public Document loadDocument(File file) {
        try {
            return FileSystemDocumentLoader.loadDocument(file.toPath(), parser);
        } catch (Exception e) {
            log.warn("Không thể parse file: {}", file.getName(), e);
            // Nếu không parse được (ví dụ file ảnh), trả về text thô
            return Document.from(file.getName());
        }
    }

    /**
     * Xóa file tạm
     */
    public void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}