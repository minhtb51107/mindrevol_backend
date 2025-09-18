// Khai báo package và import các thư viện cần thiết
package com.example.demo.controller.auth;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.model.auth.User;
import com.example.demo.model.auth.VerificationCode;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.repository.auth.VerificationCodeRepository;
import com.example.demo.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

// Cho phép truy cập từ domain frontend (React/Vue.js thường chạy ở port 5173)
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
// Đánh dấu đây là controller xử lý REST API
@RestController
// Base path cho tất cả endpoint trong controller này
@RequestMapping("/api/auth")
// Tự động tạo constructor với các tham số final
@RequiredArgsConstructor
public class AuthController {

    // Inject các dependency cần thiết thông qua constructor
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeRepository verificationCodeRepository;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;

    // Endpoint gửi mã xác minh qua email
    @PostMapping("/send-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> request) {
        // Lấy email từ request body
        String email = request.get("email");
        // Kiểm tra email có hợp lệ không
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email không hợp lệ");
        }

        // Tạo mã xác minh ngẫu nhiên 6 chữ số
        String code = String.format("%06d", new Random().nextInt(999999));

        // Tạo đối tượng VerificationCode và lưu vào database
        VerificationCode verificationCode = VerificationCode.builder()
            .email(email)
            .code(code)
            .createdAt(LocalDateTime.now())
            .build();
        verificationCodeRepository.save(verificationCode);

        try {
            // Tạo và cấu hình email
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(email);
            helper.setSubject("Mã xác minh đăng ký tài khoản");
            // Nội dung email với mã xác minh (định dạng HTML)
            helper.setText("Mã xác minh của bạn là: <b>" + code + "</b>", true);
            // Gửi email
            mailSender.send(message);
        } catch (MessagingException e) {
            // Xử lý lỗi gửi email
            return ResponseEntity.status(500).body("Lỗi gửi email: " + e.getMessage());
        }

        // Trả về thông báo thành công
        return ResponseEntity.ok("Đã gửi mã xác minh đến email.");
    }

    // Endpoint đăng ký tài khoản mới
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email đã tồn tại");
        }

        // Lấy mã xác minh mới nhất cho email từ database
        Optional<VerificationCode> codeOpt = verificationCodeRepository.findTopByEmailOrderByCreatedAtDesc(request.getEmail());

        // Kiểm tra mã xác minh có hợp lệ không
        if (codeOpt.isEmpty() || !codeOpt.get().getCode().equals(request.getVerificationCode())) {
            return ResponseEntity.badRequest().body("Mã xác minh không đúng hoặc đã hết hạn");
        }

        // Tạo user mới và lưu vào database
        User user = new User();
        user.setEmail(request.getEmail());
        // Mã hóa mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setAge(request.getAge());
        user.setRegisteredAt(LocalDateTime.now());
        userRepository.save(user);

        // Trả về thông báo thành công
        return ResponseEntity.ok("Đăng ký thành công");
    }

    // Endpoint đăng nhập bằng email và mật khẩu
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return userRepository.findByEmail(request.getEmail())
            .map(user -> {
                if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                    String token = jwtUtil.generateToken(user.getEmail());
                    // Trả về JSON object thay vì chỉ token
                    Map<String, String> response = new HashMap<>();
                    response.put("token", token);
                    response.put("message", "Đăng nhập thành công");
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(401).body("Sai mật khẩu");
                }
            })
            .orElseGet(() -> ResponseEntity.status(404).body("Email không tồn tại"));
    }

    // Endpoint đăng nhập bằng Google
 // Endpoint đăng nhập bằng Google
    @PostMapping("/login/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        System.out.println("\n🔥 ===== /api/auth/login/google CALLED =====");
        
        // 🔍 LOG 1: Kiểm tra request body
        System.out.println("📦 Full request body: " + request);
        
        // Lấy Google ID token từ request
        String idToken = request.get("idToken");
        
        // 🔍 LOG 2: Kiểm tra idToken từ client
        System.out.println("📋 idToken from client: " + (idToken != null ? 
            "PRESENT (length: " + idToken.length() + ")" : "NULL OR MISSING!"));
        
        if (idToken != null && idToken.length() > 100) {
            System.out.println("🔍 idToken preview: " + idToken.substring(0, 50) + "..." + idToken.substring(idToken.length() - 20));
        }

        // Tạo Google ID token verifier với client ID của ứng dụng
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
            .Builder(new NetHttpTransport(), new JacksonFactory())
            .setAudience(Collections.singletonList("758520677856-j98pg9k2fju9545q0ffffmsnr9b1qtk9.apps.googleusercontent.com"))
            .build();

        try {
            // 🔍 LOG 3: Trước khi verify với Google
            System.out.println("🔄 Verifying Google ID token...");
            
            // Xác minh Google ID token
            GoogleIdToken token = verifier.verify(idToken);
            
            if (token != null) {
                System.out.println("✅ Google ID token verification SUCCESSFUL");
                
                // Lấy thông tin từ token
                GoogleIdToken.Payload payload = token.getPayload();
                String email = payload.getEmail();
                String name = (String) payload.get("name");

                // 🔍 LOG 4: Thông tin user từ Google
                System.out.println("👤 User info from Google - Email: " + email + ", Name: " + name);

                // Tìm user trong database hoặc tạo mới nếu chưa có
                User user = userRepository.findByEmail(email).orElseGet(() -> {
                    System.out.println("➡️ User not found, creating new user...");
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFullName(name);
                    newUser.setPassword(""); // Không cần mật khẩu cho đăng nhập Google
                    newUser.setRegisteredAt(LocalDateTime.now());
                    User savedUser = userRepository.save(newUser);
                    System.out.println("✅ New user created: " + savedUser.getEmail());
                    return savedUser;
                });

                System.out.println("✅ User resolved: " + user.getEmail());
                
                // 🔍 LOG 5: Trước khi tạo JWT
                System.out.println("🛠️ Generating JWT token...");
                
                // Tạo JWT token cho user
                String jwt = jwtUtil.generateToken(user.getEmail());
                
                // 🔍 LOG 6: Kiểm tra JWT token được tạo ra
                System.out.println("✅ JWT token generated: '" + jwt + "'");
                System.out.println("📏 JWT token length: " + jwt.length());
                int dotCount = jwt.length() - jwt.replace(".", "").length();
                System.out.println("🔢 Number of '.' in JWT: " + dotCount);
                
                // Trả về JSON object
                Map<String, String> response = new HashMap<>();
                response.put("token", jwt);
                response.put("message", "Đăng nhập Google thành công");
                
                System.out.println("📤 Response to client: " + response);
                System.out.println("🔥 ===== /api/auth/login/google COMPLETED =====\n");
                return ResponseEntity.ok(response);
                
            } else {
                // Token không hợp lệ
                System.err.println("❌ Google ID token verification FAILED: Token is null");
                return ResponseEntity.status(401).body("Token không hợp lệ");
            }
        } catch (Exception e) {
            // Xử lý lỗi xác minh token
            System.err.println("❌ EXCEPTION during Google token verification: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(401).body("Lỗi xác minh Google ID token: " + e.getMessage());
        }
    }
}