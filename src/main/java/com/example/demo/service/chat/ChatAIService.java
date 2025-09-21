package com.example.demo.service.chat;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.service.chat.agent.OrchestratorService;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.document.DocumentIngestionService;
import dev.langchain4j.memory.ChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async; // <-- Import @Async
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter; // <-- Import SseEmitter

import java.io.IOException; // <-- Import IOException
import java.util.UUID;

@Slf4j
@Service
public class ChatAIService {

    private final OrchestratorService orchestratorService;
    private final ChatSessionRepository sessionRepo;
    private final LangChainChatMemoryService langChainChatMemoryService;
    private final DocumentIngestionService documentIngestionService;
    private final String defaultPipelineName;

    @Autowired
    public ChatAIService(OrchestratorService orchestratorService,
                         ChatSessionRepository sessionRepo,
                         LangChainChatMemoryService langChainChatMemoryService,
                         DocumentIngestionService documentIngestionService,
                         @Value("${app.default-pipeline-name:default-rag}") String defaultPipelineName) {
        this.orchestratorService = orchestratorService;
        this.sessionRepo = sessionRepo;
        this.langChainChatMemoryService = langChainChatMemoryService;
        this.documentIngestionService = documentIngestionService;
        this.defaultPipelineName = defaultPipelineName;
    }

    /**
     * Phương thức xử lý yêu cầu chat đồng bộ (cũ).
     */
    public String processMessages(Long sessionId, String prompt, MultipartFile file, User user) {
        // ... (phần này giữ nguyên không đổi)
        try {
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(sessionId);

            String tempFileId = null;
            if (file != null && !file.isEmpty()) {
                log.debug("Processing attached file: {}", file.getOriginalFilename());
                tempFileId = UUID.randomUUID().toString();
                documentIngestionService.ingestTemporaryFile(file, user, session.getId(), tempFileId);
            }

            RagContext context = RagContext.builder()
                    .initialQuery(prompt)
                    .user(user)
                    .session(session)
                    .chatMemory(chatMemory)
                    .tempFileId(tempFileId)
                    .pipelineName(this.defaultPipelineName)
                    .build();

            log.info("Delegating blocking request to OrchestratorService for session {}", sessionId);
            // Phương thức orchestrate cũ trả về String, nhưng chúng ta sẽ sửa nó
            // Để tương thích, tạm thời chúng ta sẽ giả định nó trả về String
            // Sau khi sửa orchestrate(), dòng này sẽ cần xem lại
            return orchestratorService.orchestrate(context);

        } catch (Exception e) {
            log.error("Exception at ChatAIService entry point: {}", e.getMessage(), e);
            return "Rất tiếc, đã có lỗi xảy ra. Vui lòng thử lại sau.";
        }
    }

    /**
     * Phương thức MỚI để xử lý yêu cầu chat streaming.
     * Chạy trên một luồng riêng để không block request HTTP ban đầu.
     */
    @Async("secureChatTaskExecutor")
    public void processStreamMessages(Long sessionId, String prompt, MultipartFile file, User user, SseEmitter emitter) {
        try {
            // Logic chuẩn bị session, memory, file tương tự như phương thức đồng bộ
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(sessionId);
            String tempFileId = null;
            if (file != null) {
                tempFileId = UUID.randomUUID().toString();
                documentIngestionService.ingestTemporaryFile(file, user, session.getId(), tempFileId);
            }

            // Tạo context và truyền emitter vào
            RagContext context = RagContext.builder()
                    .initialQuery(prompt)
                    .user(user)
                    .session(session)
                    .chatMemory(chatMemory)
                    .tempFileId(tempFileId)
                    .pipelineName(this.defaultPipelineName)
                    .sseEmitter(emitter) // <-- Đặt emitter vào context
                    .build();

            // Gọi orchestrator. Giờ đây phương thức này sẽ xử lý việc gửi dữ liệu
            // qua emitter và chúng ta không cần giá trị trả về.
            orchestratorService.orchestrate(context);

        } catch (Exception e) {
            log.error("Exception in stream processing for session {}: {}", sessionId, e.getMessage(), e);
            try {
                // Nếu có lỗi, gửi một sự kiện 'error' về client
                emitter.send(SseEmitter.event().name("error").data("Đã có lỗi xảy ra trong quá trình xử lý."));
            } catch (IOException ex) {
                log.warn("Failed to send error event to client for session {}", sessionId, ex);
            } finally {
                // Đảm bảo emitter luôn được đóng lại, dù thành công hay thất bại
                emitter.complete();
            }
        }
    }
}