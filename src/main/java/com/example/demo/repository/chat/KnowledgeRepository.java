package com.example.demo.repository.chat;

import com.example.demo.dto.chat.DocumentInfoDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class KnowledgeRepository {
	
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
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
    
    /**
     * ✅ HÀM MỚI: Tìm tất cả các sessionId duy nhất đang tồn tại trong vector store
     * cho các file tạm thời. Dùng cho cleanup task.
     * @return Danh sách các sessionId.
     */
    public List<Long> findDistinctSessionIdsForTempFiles() {
        String sql = "SELECT DISTINCT(metadata ->> 'sessionId')::bigint FROM langchain4j_embedding " +
                     "WHERE metadata ->> 'docType' = :docType AND metadata ->> 'sessionId' IS NOT NULL";
        
        Map<String, String> params = new HashMap<>();
        params.put("docType", "temp_file");

        return namedParameterJdbcTemplate.queryForList(sql, params, Long.class);
    }

    /**
     * Phiên bản có cả userId, dùng khi người dùng chủ động xóa session.
     */
    public int deleteTemporaryDocumentsBySession(Long sessionId, Long userId) {
        String sql = "DELETE FROM langchain4j_embedding WHERE metadata ->> 'docType' = :docType " +
                     "AND metadata ->> 'sessionId' = :sessionId " +
                     "AND metadata ->> 'userId' = :userId";
        
        Map<String, Object> params = new HashMap<>();
        params.put("docType", "temp_file");
        params.put("sessionId", sessionId.toString());
        params.put("userId", userId.toString());

        return namedParameterJdbcTemplate.update(sql, params);
    }
    
    /**
     * ✅ HÀM MỚI (Overloaded): Xóa các document tạm thời của một session mà không cần userId.
     * Dùng cho tác vụ dọn dẹp nền (cleanup task) khi không có thông tin user,
     * vì session tương ứng đã bị xóa khỏi CSDL chính.
     *
     * @param sessionId ID của phiên chat mồ côi cần dọn dẹp.
     * @return Số lượng chunks (mẩu tin) đã bị xóa.
     */
    public int deleteTemporaryDocumentsBySession(Long sessionId) {
        // Câu lệnh SQL này tìm và xóa các bản ghi trong bảng langchain4j_embedding
        // dựa trên hai điều kiện trong metadata:
        // 1. 'docType' phải là 'temp_file'
        // 2. 'sessionId' phải khớp với ID của session mồ côi được cung cấp
        String sql = "DELETE FROM langchain4j_embedding WHERE metadata ->> 'docType' = :docType " +
                     "AND metadata ->> 'sessionId' = :sessionId";
        
        // Chúng ta sử dụng Map để truyền tham số vào câu lệnh SQL một cách an toàn,
        // tránh các lỗi SQL Injection.
        Map<String, Object> params = new HashMap<>();
        params.put("docType", "temp_file");
        params.put("sessionId", sessionId.toString());

        // Thực thi câu lệnh DELETE và trả về số dòng (chunks) đã bị ảnh hưởng (bị xóa).
        return namedParameterJdbcTemplate.update(sql, params);
    }
}