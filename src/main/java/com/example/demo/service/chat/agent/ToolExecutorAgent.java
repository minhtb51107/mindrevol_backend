package com.example.demo.service.chat.agent;

import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.tools.WebPageContentFetcher;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service("ToolAgent")
@RequiredArgsConstructor
public class ToolExecutorAgent implements Agent {

    private final ChatLanguageModel chatLanguageModel;
    private final WebSearchEngine webSearchEngine;
    private final WebPageContentFetcher contentFetcher;

    @Override
    public String getName() {
        return "ToolAgent";
    }

    @Override
    public String getDescription() {
        return "Sử dụng các công cụ chuyên dụng để trả lời câu hỏi về thông tin cần truy cập API bên ngoài, ví dụ: thời tiết, thời gian hiện tại, và thông tin tài chính, chứng khoán, cổ phiếu theo thời gian thực.";
    }

    @StructuredPrompt("Tạo ra 3 câu hỏi tìm kiếm Google đa dạng và hiệu quả để trả lời cho câu hỏi sau của người dùng: '{{question}}'. " +
            "Các câu hỏi tìm kiếm nên tập trung vào thông tin mới nhất (ví dụ: thêm năm hiện tại). " +
            "Chỉ trả lời bằng một danh sách các câu hỏi, mỗi câu trên một dòng, không có đánh số hay gạch đầu dòng.")
    public static class SearchQueryGenerator {
        public String question;
    }

    @Override
    public RagContext execute(RagContext context) {
        // <<< SỬA LỖI: Thay thế getInitialQuery() bằng getQuery() >>>
        // Giờ đây, 'rewrittenQuestion' sẽ chứa câu hỏi đã được làm giàu ngữ cảnh
        // ví dụ: "Search the internet for the Prime Minister of Japan."
        String rewrittenQuestion = context.getQuery();
        log.debug("Executing Research Workflow for rewritten query: '{}'", rewrittenQuestion);

        // BƯỚC 1: TẠO CÁC TRUY VẤN TÌM KIẾM DỰA TRÊN CÂU HỎI ĐÚNG
        SearchQueryGenerator queryGenerator = new SearchQueryGenerator();
        queryGenerator.question = rewrittenQuestion; // <<< SỬA LỖI
        Prompt prompt = StructuredPromptProcessor.toPrompt(queryGenerator);
        
        String rawQueries = chatLanguageModel.generate(prompt.text());
        List<String> searchQueries = Arrays.stream(rawQueries.split("\n"))
                                           .filter(q -> !q.trim().isEmpty())
                                           .collect(Collectors.toList());
        log.info("Generated search queries: {}", searchQueries);

        // BƯỚC 2: TÌM KIẾM VÀ LỌC KẾT QUẢ
        List<WebSearchOrganicResult> topResults = searchQueries.stream()
                .parallel()
                .flatMap(query -> webSearchEngine.search(query).results().stream())
                .distinct()
                .limit(3)
                .collect(Collectors.toList());

        if (topResults.isEmpty()) {
            context.setReply("Xin lỗi, tôi không tìm thấy thông tin nào liên quan trên mạng cho câu hỏi này.");
            return context;
        }
        
        // BƯỚC 3: ĐỌC VÀ TRÍCH XUẤT NỘI DUNG
        String fetchedContents = topResults.stream()
                .map(result -> {
                    log.info("Fetching content from: {}", result.url());
                    String content = contentFetcher.fetchContent(result.url().toString());
                    if (content.length() > 3000) {
                        content = content.substring(0, 3000);
                    }
                    return "--- Nguồn: " + result.title() + " ---\n" + content;
                })
                .collect(Collectors.joining("\n\n"));

        // BƯỚC 4: TỔNG HỢP VÀ PHÂN TÍCH DỰA TRÊN CÂU HỎI ĐÚNG
        PromptTemplate finalPromptTemplate = PromptTemplate.from(
                "Bạn là một trợ lý AI phân tích thông tin chuyên nghiệp. " +
                "Nhiệm vụ của bạn là dựa vào các nội dung được trích xuất từ nhiều nguồn tin tức và trang web dưới đây, hãy trả lời câu hỏi gốc của người dùng một cách chính xác, khách quan và đầy đủ nhất. " +
                "Hãy ưu tiên các thông tin có vẻ mới và đáng tin cậy. Nếu có thông tin mâu thuẫn, hãy chỉ ra điều đó. " +
                "Tuyệt đối không tự bịa đặt thông tin không có trong nội dung được cung cấp.\n\n" +
                "Câu hỏi của người dùng: '{{question}}'\n\n" +
                "Nội dung đã trích xuất:\n{{contents}}"
        );

        Map<String, Object> variables = Map.of(
                "question", rewrittenQuestion, // <<< SỬA LỖI
                "contents", fetchedContents
        );
        
        String finalAnswer = chatLanguageModel.generate(finalPromptTemplate.apply(variables).text());

        context.setReply(finalAnswer);
        return context;
    }
}