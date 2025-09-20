package com.example.demo.service.chat;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.chat.ChatMessageDTO;
import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatMessage;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.model.chat.EmotionContext;
import com.example.demo.model.chat.ConversationState;
import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.repository.chat.ConversationStateRepository.ConversationStateRepository;
import com.example.demo.repository.chat.EmotionContextRepository.EmotionContextRepository;
import com.example.demo.repository.chat.UserPreferenceRepository.UserPreferenceRepository;
import com.example.demo.service.chat.context.ContextCompressionService;
import com.example.demo.service.chat.emotion.EmotionAnalysisService;
import com.example.demo.service.chat.preference.UserPreferenceService;
import com.example.demo.service.chat.reranking.RerankingService;
import com.example.demo.service.chat.state.ConversationStateService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;

import com.example.demo.service.chat.fallback.FallbackService;
import com.example.demo.service.document.FileProcessingService;
import com.example.demo.service.document.DocumentIngestionService;
import com.example.demo.service.chat.memory.langchain.ConversationSummaryService;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.steps.GenerationStep;
import com.example.demo.service.chat.orchestration.steps.MemoryQueryStep;
import com.example.demo.service.chat.orchestration.steps.RerankingStep;
import com.example.demo.service.chat.orchestration.steps.RetrievalStep;

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAIService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageService messageService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final ConversationStateService conversationStateService;
    private final FallbackService fallbackService;

    // REPOSITORIES
    private final EmotionContextRepository emotionContextRepository;
    private final ConversationStateRepository conversationStateRepository;

    private final LangChainChatMemoryService langChainChatMemoryService;
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // ORCHESTRATION STEPS
    private final RetrievalStep retrievalStep;
    private final RerankingStep rerankingStep;
    private final GenerationStep generationStep;
    private final MemoryQueryStep memoryQueryStep;

    // SERVICES
    private final FileProcessingService fileProcessingService;
    private final DocumentIngestionService documentIngestionService;

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

            // Xử lý file đính kèm
            if (file != null && !file.isEmpty()) {
                log.debug("Processing attached file: {}", file.getOriginalFilename());
                tempFileId = UUID.randomUUID().toString();
                documentIngestionService.ingestTemporaryFile(file, user, session.getId(), tempFileId);
                log.debug("File {} ingested with tempFileId: {}", file.getOriginalFilename(), tempFileId);
            }

            runContextAnalysisAsync(session, user, prompt);

            // Bắt đầu Orchestration
            RagContext.QueryIntent intent = classifyQueryIntent(prompt);
            log.debug("Query intent classified as: {}", intent);

            RagContext context = RagContext.builder()
                    .initialQuery(prompt)
                    .user(user)
                    .session(session)
                    .chatMemory(chatMemory)
                    .intent(intent)
                    .tempFileId(tempFileId)
                    .build();

            // Chọn Pipeline và thực thi với khả năng phục hồi (Resilience)
            if (intent == RagContext.QueryIntent.RAG_QUERY) {
                log.debug("Handling as RAG_QUERY. Running full RAG pipeline.");

                // Retrieval Step (Critical)
                try {
                    context = retrievalStep.execute(context);
                } catch (Exception e) {
                    log.error("RAG Pipeline - CRITICAL: RetrievalStep failed. Aborting pipeline.", e);
                    return fallbackService.getKnowledgeRetrievalErrorResponse();
                }

                // Reranking Step (Non-Critical)
                try {
                    context = rerankingStep.execute(context);
                } catch (Exception e) {
                    log.warn("RAG Pipeline - NON-CRITICAL: RerankingStep failed. Proceeding with un-reranked results.", e);
                    // Không cần làm gì thêm, context vẫn chứa kết quả từ retrieval
                }

                // Generation Step (Critical)
                try {
                    context = generationStep.execute(context);
                } catch (Exception e) {
                    log.error("RAG Pipeline - CRITICAL: GenerationStep failed.", e);
                    return fallbackService.getGenerationErrorResponse();
                }

            } else if (intent == RagContext.QueryIntent.CHITCHAT) {
                log.debug("Handling as CHITCHAT. Skipping RAG.");
                context = generationStep.execute(context);

            } else { // MEMORY_QUERY
                log.debug("Handling as MEMORY_QUERY. Using direct memory handler.");
                context = memoryQueryStep.execute(context);
            }

            String reply = context.getReply();

            // Cập nhật bộ nhớ và lưu trữ
            updateMemoryAndPersist(session, prompt, reply);

            return reply;

        } catch (Exception e) {
            log.error("Lỗi xử lý processMessages không mong muốn: {}", e.getMessage(), e);
            // Cân nhắc: Thêm logic xóa tempFileId khỏi vector store nếu ingest lỗi
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

            log.debug("Đã lưu 2 tin nhắn (User: {}, AI: {}) vào vector store cho session {}",
                    userMessage.getId(), aiMessage.getId(), session.getId());

        } catch (Exception e) {
            log.warn("Không thể lưu message embeddings vào vector store: {}", e.getMessage());
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

            log.debug("Đã cập nhật Context (Emotion, State) bất đồng bộ cho session {}", session.getId());
        } catch (Exception e) {
            log.warn("Lỗi cập nhật context bất đồng bộ: {}", e.getMessage());
        }
    }

    private RagContext.QueryIntent classifyQueryIntent(String query) {
        try {
            String systemPrompt = """
                Bạn là một AI phân loại ý định truy vấn cực kỳ chính xác.
                Nhiệm vụ của bạn là đọc truy vấn của người dùng và phân loại nó vào MỘT trong ba loại sau:

                1.  RAG_QUERY:
                    - Người dùng đang hỏi về thông tin cụ thể, sự kiện, dữ liệu, hoặc kiến thức.
                    - Người dùng đang hỏi về nội dung của các tệp tin (ví dụ: "file X nói về cái gì?", "tóm tắt file Y", "dựa vào file tôi vừa gửi...").
                    - Người dùng hỏi về các chủ đề chuyên môn (ví dụ: "giải thích code...", "lỗi...").
                    - Bất kỳ câu hỏi nào cần tra cứu kiến thức để trả lời.

                2.  MEMORY_QUERY:
                    - Người dùng đang hỏi về chính cuộc trò chuyện (ví dụ: "tôi vừa nói gì?", "nhắc lại lời tôi").

                3.  CHITCHAT:
                    - Người dùng đang chào hỏi, tạm biệt, cảm ơn, hoặc nói chuyện phiếm không có mục đích thông tin rõ ràng.
                    - Ví dụ: "Chào bạn", "Bạn khỏe không?", "Cảm ơn", "Tuyệt vời".

                Chỉ trả lời bằng MỘT TỪ: RAG_QUERY, MEMORY_QUERY, hoặc CHITCHAT.
                """;

            String response = chatLanguageModel.generate(systemPrompt + "\n\nTruy vấn: " + query);

            if (response.contains("MEMORY_QUERY")) {
                return RagContext.QueryIntent.MEMORY_QUERY;
            } else if (response.contains("CHITCHAT")) {
                return RagContext.QueryIntent.CHITCHAT;
            } else {
                return RagContext.QueryIntent.RAG_QUERY;
            }
        } catch (Exception e) {
            log.warn("Query intent classification failed: {}. Falling back to RAG_QUERY.", e.getMessage());
            return RagContext.QueryIntent.RAG_QUERY;
        }
    }
}