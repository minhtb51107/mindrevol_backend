package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RetrievalStep implements RagStep {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Override
    public RagContext execute(RagContext context) {
        // 1. Nhúng câu truy vấn
        Embedding queryEmbedding = embeddingModel.embed(context.getInitialQuery()).content();
        context.setQueryEmbedding(queryEmbedding);

        // 2. Xây dựng bộ lọc Metadata
        // Chúng ta sẽ xây dựng cây logic bằng cách kết hợp từng cặp filter

        // 2a. Điều kiện cơ sở 1: Tìm kiếm tin nhắn trong session này
        Filter sessionMessageFilter = new And(
                new IsEqualTo("sessionId", context.getSession().getId().toString()),
                new IsEqualTo("docType", "message")
        );

        // 2b. Điều kiện cơ sở 2: Tìm kiếm trong kho tri thức của user
        Filter userKnowledgeFilter = new And(
                new IsEqualTo("userId", context.getUser().getId().toString()),
                new IsEqualTo("docType", "knowledge")
        );
        
        // 2c. Kết hợp hai điều kiện cơ sở đầu tiên bằng OR
        // finalFilter bây giờ là: (sessionMessageFilter OR userKnowledgeFilter)
        Filter finalFilter = new Or(sessionMessageFilter, userKnowledgeFilter);

        // 2d. ✅ LOGIC MỚI: Nếu có file tạm, lồng thêm điều kiện OR thứ ba
        if (context.getTempFileId() != null && !context.getTempFileId().isBlank()) {
            
            // Tạo filter cho file tạm
            Filter tempFileFilter = new And(
                // Phải lồng 2 And lại với nhau vì constructor chỉ nhận 2 tham số
                new And(
                    new IsEqualTo("docType", "temp_file"),
                    new IsEqualTo("sessionId", context.getSession().getId().toString())
                ),
                new IsEqualTo("tempFileId", context.getTempFileId())
            );
            
            // Cập nhật finalFilter bằng cách lồng nó với điều kiện mới
            // finalFilter bây giờ là: ( (sessionMessageFilter OR userKnowledgeFilter) OR tempFileFilter )
            finalFilter = new Or(finalFilter, tempFileFilter);
        }

        context.setMetadataFilter(finalFilter);

        // 3. Tạo yêu cầu tìm kiếm
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(20) // Lấy nhiều hơn để Rerank
                .filter(finalFilter)
                .build();

        // 4. Thực thi tìm kiếm
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);
        context.setRetrievedMatches(searchResult.matches());

        return context;
    }
    
    
}