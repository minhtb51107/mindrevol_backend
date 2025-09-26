// src/main/java/com/example/demo/service/chat/ChatAIService.java
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
     * Phương thức xử lý yêu cầu chat đồng bộ (blocking).
     */
    public String processMessages(Long sessionId, String prompt, MultipartFile file, User user) {
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
            // ✅ SỬA LỖI: Gọi orchestrate với cả prompt và context
            return orchestratorService.orchestrate(prompt, context);

        } catch (Exception e) {
            log.error("Exception at ChatAIService entry point: {}", e.getMessage(), e);
            return "Rất tiếc, đã có lỗi xảy ra. Vui lòng thử lại sau.";
        }
    }

//    /**
//     * Phương thức MỚI để xử lý yêu cầu chat streaming.
//     */
//    @Async("secureChatTaskExecutor")
//    public void processStreamMessages(Long sessionId, String prompt, MultipartFile file, User user, SseEmitter emitter) {
//        try {
//            ChatSession session = sessionRepo.findById(sessionId)
//                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));
//            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(sessionId);
//            String tempFileId = null;
//            if (file != null) {
//                tempFileId = UUID.randomUUID().toString();
//                documentIngestionService.ingestTemporaryFile(file, user, session.getId(), tempFileId);
//            }
//
//            RagContext context = RagContext.builder()
//                    .initialQuery(prompt)
//                    .user(user)
//                    .session(session)
//                    .chatMemory(chatMemory)
//                    .tempFileId(tempFileId)
//                    .pipelineName(this.defaultPipelineName)
//                    .sseEmitter(emitter)
//                    .build();
//
//            // ✅ SỬA LỖI: Gọi orchestrate với cả prompt và context
//            // Orchestrator sẽ tự xử lý việc gửi dữ liệu qua emitter nếu cần (RAG/ChitChat)
//            // hoặc trả về kết quả trực tiếp (TOOL).
//            String result = orchestratorService.orchestrate(prompt, context);
//
//            // ✅ XỬ LÝ KẾT QUẢ TỪ TOOL AGENT:
//            // Nếu result không rỗng, có nghĩa là ToolAgent đã chạy và trả về kết quả.
//            // Các agent RAG/ChitChat sẽ tự xử lý emitter và trả về null hoặc chuỗi rỗng.
//            // Chúng ta cần gửi kết quả này về cho client qua SseEmitter.
//            if (result != null && !result.isEmpty() && !context.isStreamCompleted()) {
//                 emitter.send(SseEmitter.event().name("message").data(result));
//            }
//
//        } catch (Exception e) {
//            log.error("Exception in stream processing for session {}: {}", sessionId, e.getMessage(), e);
//            try {
//                emitter.send(SseEmitter.event().name("error").data("Đã có lỗi xảy ra trong quá trình xử lý."));
//            } catch (IOException ex) {
//                log.warn("Failed to send error event to client for session {}", sessionId, ex);
//            }
//        } finally {
//            // Đảm bảo emitter luôn được đóng lại, dù thành công hay thất bại
//            // Kiểm tra xem nó có đang mở không trước khi đóng
//            if (!context.isStreamCompleted()) {
//                emitter.complete();
//            }
//        }
//    }
}