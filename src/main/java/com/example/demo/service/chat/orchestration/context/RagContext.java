package com.example.demo.service.chat.orchestration.context;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
// ✅ BƯỚC 1: IMPORT LỚP QUERYINTENT ĐÚNG
import com.example.demo.service.chat.orchestration.rules.QueryIntent; 
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RagContext {

    // --- Đầu vào ban đầu ---
    private String query;
    private String initialQuery;
    private final User user;
    private final ChatSession session;
    private final ChatMemory chatMemory;
    private final String tempFileId;
    
    private String pipelineName;
    
    private SseEmitter sseEmitter;

    // --- Dữ liệu được xử lý qua các bước ---

    // (Từ ClassificationStep)
    private QueryIntent intent; // <-- Bây giờ nó sẽ dùng QueryIntent được import ở trên
    
    private String transformedQuery;

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

    // ✅ BƯỚC 2: XÓA BỎ TOÀN BỘ ĐỊNH NGHĨA ENUM BỊ THỪA Ở ĐÂY
    /* public enum QueryIntent {
        RAG_QUERY,
        CHITCHAT,
        MEMORY_QUERY
    }
    */
}