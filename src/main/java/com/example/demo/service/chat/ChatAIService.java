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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
     * ✅ 1. ĐÃ THÊM THAM SỐ 'boolean regenerate'
     */
    @Transactional
    public String processMessages(Long sessionId, String prompt, MultipartFile file, User user, boolean regenerate) {
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
            // ✅ 2. TRUYỀN CỜ 'regenerate' VÀO PHƯƠNG THỨC ORCHESTRATE
            return orchestratorService.orchestrate(prompt, context, regenerate);

        } catch (Exception e) {
            log.error("Exception at ChatAIService entry point: {}", e.getMessage(), e);
            return "Rất tiếc, đã có lỗi xảy ra. Vui lòng thử lại sau.";
        }
    }

    // Các phương thức streaming khác (nếu có) có thể được cập nhật tương tự nếu cần.
}
