package com.example.demo.service.chat.orchestration.context;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RagContext {

    // --- Đầu vào ban đầu ---
    private final String initialQuery;
    private final User user;
    private final ChatSession session;
    private final ChatMemory chatMemory;

    // --- Dữ liệu được xử lý qua các bước ---

    // (Từ ClassificationStep)
    private QueryIntent intent;

    // (Từ RetrievalStep)
    private Embedding queryEmbedding;
    private Filter metadataFilter;
    private List<EmbeddingMatch<TextSegment>> retrievedMatches;

    // (Từ RerankingStep)
    private List<EmbeddingMatch<TextSegment>> rerankedMatches;

    // (Từ GenerationStep)
    private String ragContextString;
    private Map<String, Object> userPreferences;
    private List<ChatMessage> finalLcMessages;

    // --- Kết quả cuối cùng ---
    private String reply;

    // --- Enum Intent (di chuyển từ ChatAIService) ---
    public enum QueryIntent {
        RAG_QUERY,
        CHITCHAT,
        MEMORY_QUERY
    }
}