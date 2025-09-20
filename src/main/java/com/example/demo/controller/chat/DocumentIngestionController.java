package com.example.demo.controller.chat; // (Hoặc một package controller mới nếu bạn muốn)

import com.example.demo.dto.chat.DocumentInfoDTO;
import com.example.demo.model.auth.User;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.document.DocumentIngestionService;
import com.example.demo.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents") // Tạo một route API mới cho tài liệu
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    // Các dependency này là để sao chép logic xác thực user
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            // ✅ THAY ĐỔI: Chuyển sang mảng và đổi tên thành "files" (số nhiều)
            @RequestParam("files") MultipartFile[] files,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            User user = extractUserFromAuth(authHeader);
            
            // ✅ THAY ĐỔI: Kiểm tra mảng
            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "File không được để trống"));
            }

            // ✅ THÊM MỚI: Chuyển mảng sang List
            List<MultipartFile> fileList = Arrays.asList(files);

            // ✅ THAY ĐỔI: Gọi hàm service số nhiều (chúng ta sẽ tạo nó ở bước 2)
            ingestionService.ingestDocuments(fileList, user);

            return ResponseEntity.ok(Map.of("message", "Đã nạp thành công " + fileList.size() + " tệp tin."));
        
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Không thể nạp file: " + e.getMessage()));
        }
    }

    /**
     * Phương thức helper để xác thực và trích xuất thông tin user từ token
     * (Sao chép từ ChatSessionController. Bạn nên xem xét tái cấu trúc
     * phần này vào một class @Component riêng để tránh lặp code)
     */
    private User extractUserFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Thiếu token hoặc sai định dạng");
        }
        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
    
    /**
     * ✅ ENDPOINT MỚI: Lấy danh sách tất cả file đã nạp
     */
    @GetMapping
    public ResponseEntity<List<DocumentInfoDTO>> getDocuments(
            @RequestHeader("Authorization") String authHeader) {
        try {
            User user = extractUserFromAuth(authHeader);
            List<DocumentInfoDTO> documents = ingestionService.listDocuments(user);
            return ResponseEntity.ok(documents);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(null);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * ✅ ENDPOINT MỚI: Xóa một file (và tất cả các chunk của nó)
     */
    @DeleteMapping
    public ResponseEntity<?> deleteDocument(
            @RequestParam("fileName") String fileName, // Dùng RequestParam cho an toàn
            @RequestHeader("Authorization") String authHeader) {
        
        if (fileName == null || fileName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "fileName là bắt buộc"));
        }
        
        try {
            User user = extractUserFromAuth(authHeader);
            int deletedCount = ingestionService.deleteDocument(fileName, user);
            return ResponseEntity.ok(Map.of(
                "message", "Đã xóa file '" + fileName + "' và " + deletedCount + " mẩu tin."
            ));
        
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Không thể xóa file: " + e.getMessage()));
        }
    }
    
}