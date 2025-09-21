package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.fallback.FallbackService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.steps.GenerationStep;
import com.example.demo.service.chat.orchestration.steps.QueryTransformationStep;
import com.example.demo.service.chat.orchestration.steps.RerankingStep;
import com.example.demo.service.chat.orchestration.steps.RetrievalStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGAgent implements Agent {

    private final QueryTransformationStep queryTransformationStep;
    private final RetrievalStep retrievalStep;
    private final RerankingStep rerankingStep;
    private final GenerationStep generationStep;
    private final FallbackService fallbackService;

    @Override
    public String getName() {
        return "RAGAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng agent này để trả lời các câu hỏi phức tạp, đòi hỏi tra cứu thông tin, phân tích dữ liệu từ các tài liệu đã được cung cấp hoặc kiến thức nền tảng. Phù hợp cho các câu hỏi về chuyên môn, giải thích, tóm tắt file.";
    }

    @Override
    public RagContext execute(RagContext context) {
        log.debug("Executing RAGAgent for query: '{}'", context.getInitialQuery());

        // 1. Query Transformation (Non-Critical)
        try {
            context = queryTransformationStep.execute(context);
        } catch (Exception e) {
            log.warn("RAG Pipeline - NON-CRITICAL: QueryTransformationStep failed. Proceeding with original query.", e);
            context.setTransformedQuery(context.getInitialQuery()); // Fallback
        }

        // 2. Retrieval Step (Critical)
        try {
            context = retrievalStep.execute(context);
        } catch (Exception e) {
            log.error("RAG Pipeline - CRITICAL: RetrievalStep failed. Aborting pipeline.", e);
            context.setReply(fallbackService.getKnowledgeRetrievalErrorResponse());
            return context;
        }

        // 3. Reranking Step (Non-Critical)
        try {
            context = rerankingStep.execute(context);
        } catch (Exception e) {
            log.warn("RAG Pipeline - NON-CRITICAL: RerankingStep failed. Proceeding with un-reranked results.", e);
            // Context still contains results from retrieval, so we can proceed
        }

        // 4. Generation Step (Critical)
        try {
            context = generationStep.execute(context);
        } catch (Exception e) {
            log.error("RAG Pipeline - CRITICAL: GenerationStep failed.", e);
            context.setReply(fallbackService.getGenerationErrorResponse());
        }

        return context;
    }
}