package com.example.demo.repository.chat;

import com.example.demo.dto.chat.DocumentInfoDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class KnowledgeRepository {

    // Inject trình quản lý của JPA
    @PersistenceContext
    private final EntityManager em;

    /**
     * Lấy danh sách tên file (duy nhất) của một user từ kho tri thức
     */
    @SuppressWarnings("unchecked")
    public List<DocumentInfoDTO> listDocumentsForUser(Long userId) {
        // Câu lệnh SQL này truy vấn vào trường JSONB metadata
        String sql = """
            SELECT DISTINCT metadata->>'fileName' as fileName 
            FROM message_embeddings 
            WHERE metadata->>'userId' = :userId 
            AND metadata->>'docType' = 'knowledge'
            ORDER BY fileName ASC
        """;

        Query query = em.createNativeQuery(sql, String.class);
        query.setParameter("userId", userId.toString());

        List<String> fileNames = query.getResultList();
        
        // Chuyển danh sách String sang DTO
        return fileNames.stream()
                .map(DocumentInfoDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Xóa tất cả các mẩu (chunks) của một file khỏi kho tri thức
     */
    @Transactional // Rất quan trọng vì đây là lệnh DELETE
    public int deleteDocument(String fileName, Long userId) {
        String sql = """
            DELETE FROM message_embeddings 
            WHERE metadata->>'fileName' = :fileName 
            AND metadata->>'userId' = :userId 
            AND metadata->>'docType' = 'knowledge'
        """;

        Query query = em.createNativeQuery(sql);
        query.setParameter("fileName", fileName);
        query.setParameter("userId", userId.toString());

        // Trả về số lượng mẩu (chunks) đã bị xóa
        return query.executeUpdate();
    }
}