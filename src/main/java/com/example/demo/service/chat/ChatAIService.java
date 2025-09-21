package com.example.demo.service.chat;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.model.chat.ConversationState;
import com.example.demo.model.chat.EmotionContext;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
import com.example.demo.service.chat.agent.OrchestratorService;
import com.example.demo.service.chat.emotion.EmotionAnalysisService;
import com.example.demo.service.chat.fallback.FallbackService;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.state.ConversationStateService;
import com.example.demo.service.document.DocumentIngestionService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAIService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageService messageService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final ConversationStateService conversationStateService;
    private final FallbackService fallbackService;
    private final LangChainChatMemoryService langChainChatMemoryService;
    private final DocumentIngestionService documentIngestionService;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    // REPOSITORIES
    private final EmotionContextRepository emotionContextRepository;
    private final ConversationStateRepository conversationStateRepository;

    // --- NEW ORCHESTRATION LAYER ---
    private final OrchestratorService orchestratorService;

    public String processMessages(Long sessionId, String prompt, MultipartFile file, User user) {
        String tempFileId = null;
        try {
            ChatSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session không tồn tại"));

            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(sessionId);

            if (chatMemory.messages().isEmpty()) {
                log.debug("Chat memory for session {} is empty. Hydrating from database...", sessionId);
                hydrateChatMemoryFromDB(chatMemory, sessionId);
            }

            // Handle file attachments
            if (file != null && !file.isEmpty()) {
                log.debug("Processing attached file: {}", file.getOriginalFilename());
                tempFileId = UUID.randomUUID().toString();
                documentIngestionService.ingestTemporaryFile(file, user, session.getId(), tempFileId);
                log.debug("File {} ingested with tempFileId: {}", file.getOriginalFilename(), tempFileId);
            }

            // Run async context analysis (emotion, state, etc.)
            runContextAnalysisAsync(session, user, prompt);

            // --- START ORCHESTRATION ---
            // 1. Create the initial context object
            RagContext context = RagContext.builder()
                    .initialQuery(prompt)
                    .user(user)
                    .session(session)
                    .chatMemory(chatMemory)
                    .tempFileId(tempFileId)
                    .build();

            // 2. Delegate the entire core logic to the Orchestrator
            log.debug("Delegating to OrchestratorService...");
            String reply = orchestratorService.orchestrate(context);
            
            // 3. Update memory and persist the conversation
            updateMemoryAndPersist(session, prompt, reply);

            return reply;

        } catch (Exception e) {
            log.error("Unhandled exception in processMessages: {}", e.getMessage(), e);
            // Consider: Add logic to clean up tempFileId from vector store if ingest fails
            if (tempFileId != null) {
                // TODO: Implement a cleanup mechanism for temporary files in vector store
                log.warn("An error occurred. The temporary file with ID {} might need manual cleanup.", tempFileId);
            }
            return fallbackService.getEmergencyResponse();
        }
    }

    private void updateMemoryAndPersist(ChatSession session, String userQuery, String aiReply) {
        try {
            ChatMemory chatMemory = langChainChatMemoryService.getChatMemory(session.getId());
            chatMemory.add(UserMessage.from(userQuery));
            chatMemory.add(AiMessage.from(aiReply));

            ChatMessage userMsgDb = messageService.saveMessage(session, "user", userQuery);
            ChatMessage aiMsgDb = messageService.saveMessage(session, "assistant", aiReply);
            saveMessagesToVectorStore(userMsgDb, aiMsgDb, session);
        } catch (Exception e) {
            log.error("Failed to update memory and persist messages for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }


    @Async
    private void saveMessagesToVectorStore(ChatMessage userMessage, ChatMessage aiMessage, ChatSession session) {
        try {
            TextSegment userSegment = createSegmentFromMessage(userMessage, session);
            TextSegment aiSegment = createSegmentFromMessage(aiMessage, session);

            Embedding userEmbedding = embeddingModel.embed(userSegment).content();
            Embedding aiEmbedding = embeddingModel.embed(aiSegment).content();

            embeddingStore.add(userEmbedding, userSegment);
            embeddingStore.add(aiEmbedding, aiSegment);

            log.debug("Saved 2 messages (User: {}, AI: {}) to vector store for session {}",
                    userMessage.getId(), aiMessage.getId(), session.getId());

        } catch (Exception e) {
            log.warn("Could not save message embeddings to vector store: {}", e.getMessage());
        }
    }

    private TextSegment createSegmentFromMessage(ChatMessage message, ChatSession session) {
        Metadata metadata = Metadata.from(Map.of(
                "messageId", message.getId().toString(),
                "sessionId", session.getId().toString(),
                "senderType", message.getSender(),
                "messageTimestamp", message.getTimestamp().toString(),
                "docType", "message"
        ));

        return TextSegment.from(message.getContent(), metadata);
    }

    private void hydrateChatMemoryFromDB(ChatMemory chatMemory, Long sessionId) {
        try {
            List<ChatMessage> recentDbMessages = messageService.getRecentMessages(sessionId, 20);

            if (recentDbMessages.isEmpty()) {
                return;
            }

            for (ChatMessage dbMsg : recentDbMessages) {
                if ("user".equalsIgnoreCase(dbMsg.getSender())) {
                    chatMemory.add(UserMessage.from(dbMsg.getContent()));
                } else if ("assistant".equalsIgnoreCase(dbMsg.getSender())) {
                    chatMemory.add(AiMessage.from(dbMsg.getContent()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to hydrate chat memory from DB for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Async
    protected void runContextAnalysisAsync(ChatSession session, User user, String prompt) {
        try {
            EmotionContext emotionContext = emotionContextRepository.findByChatSession_Id(session.getId())
                    .orElseGet(() -> {
                        EmotionContext ctx = new EmotionContext();
                        ctx.setChatSession(session);
                        ctx.setUser(user);
                        return ctx;
                    });
            emotionAnalysisService.analyzeEmotion(prompt, emotionContext);
            emotionContextRepository.save(emotionContext);

            ConversationState state = conversationStateService.getOrCreateState(session.getId());
            conversationStateRepository.save(state);

            log.debug("Async context update (Emotion, State) complete for session {}", session.getId());
        } catch (Exception e) {
            log.warn("Async context update failed: {}", e.getMessage());
        }
    }

    //
    // The classifyQueryIntent method has been removed as this logic is now handled by the OrchestratorService
    //
}