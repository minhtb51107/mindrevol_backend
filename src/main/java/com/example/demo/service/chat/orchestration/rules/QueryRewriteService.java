package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class QueryRewriteService {

    private final ChatLanguageModel chatLanguageModel;

    @Autowired
    public QueryRewriteService(@Qualifier("on-demand-model") ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public String rewrite(List<ChatMessage> chatHistory, String latestUserQuery) {
        if (chatHistory == null || chatHistory.isEmpty()) {
            return latestUserQuery;
        }

        String prompt = buildFinalRewritePrompt(chatHistory, latestUserQuery);
        
        try {
            Response<AiMessage> response = chatLanguageModel.generate(new UserMessage(prompt));
            String rewrittenQuery = response.content().text().trim();

            if (rewrittenQuery.isEmpty() || rewrittenQuery.equalsIgnoreCase(latestUserQuery.trim())) {
                log.info("Query rewrite resulted in no change. Using original query: '{}'", latestUserQuery.trim());
                return latestUserQuery;
            }

            log.info("Original query: '{}' -> Rewritten query: '{}'", latestUserQuery.trim(), rewrittenQuery);
            return rewrittenQuery;
        } catch (Exception e) {
            log.error("Error during query rewriting. Falling back to original query.", e);
            return latestUserQuery;
        }
    }

    private String buildFinalRewritePrompt(List<ChatMessage> chatHistory, String latestUserQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a master AI at understanding conversation context. Your goal is to rewrite a user's latest query into a standalone, complete query, preserving the original language.\n\n");
        sb.append("RULES:\n");
        sb.append("1. **PRESERVE LANGUAGE**: The rewritten query MUST be in the same language as the user's original 'Final Question'. If the user asks in Vietnamese, you rewrite in Vietnamese.\n");
        sb.append("2. **CONTEXTUAL ACTIONS**: If the 'Final Question' is a command (e.g., 'translate it', 'summarize', 'dịch nó'), rewrite it as a specific action on the Assistant's PREVIOUS response.\n");
        sb.append("3. **FOLLOW-UPS**: If the 'Final Question' is a follow-up (e.g., 'what about him?', 'còn Việt Nam thì sao?'), combine it with context to form a full question.\n");
        sb.append("4. **NO CHANGE**: If the 'Final Question' is already a complete question, return it as is.\n");
        sb.append("5. **OUTPUT**: Your output MUST ONLY be the rewritten query. No explanations.\n\n");

        sb.append("--- EXAMPLES ---\n");
        sb.append("History:\nUser: Thủ tướng Nhật là ai?\nAssistant: ...\nFinal Question: cho tôi biết thêm về ông ấy\nRewritten Question: Cung cấp thêm thông tin về Thủ tướng Nhật Bản Ishiba Shigeru.\n\n");
        sb.append("History:\nUser: Tell me about Shigeru Ishiba.\nAssistant: (provides a long English text)\nFinal Question: dịch sang tiếng việt\nRewritten Question: Dịch đoạn văn sau sang tiếng Việt: \"Shigeru Ishiba is a prominent Japanese politician...\"\n\n");
        sb.append("History:\nUser: Kể cho tôi về thủ tướng Pháp.\nAssistant: (cung cấp một đoạn văn dài bằng tiếng Việt)\nFinal Question: Dịch sang tiếng Anh\nRewritten Question: Translate the following text to English: \"(nội dung về thủ tướng Pháp)\"\n\n");
        
        sb.append("--- CURRENT TASK ---\n");
        sb.append("Conversation History:\n");
        
        int historyLimit = 2; 
        int startIndex = Math.max(0, chatHistory.size() - historyLimit);
        List<ChatMessage> recentHistory = chatHistory.subList(startIndex, chatHistory.size());

        for (ChatMessage message : recentHistory) {
            if (message instanceof UserMessage) {
                sb.append("User: ").append(message.text().trim()).append("\n");
            } else if (message instanceof AiMessage) {
                String assistantResponse = message.text().trim();
                if (assistantResponse.length() > 300) {
                    assistantResponse = assistantResponse.substring(0, 300) + "...";
                }
                sb.append("Assistant: ").append(assistantResponse).append("\n");
            }
        }

        sb.append("\nFinal Question: ").append(latestUserQuery.trim());
        sb.append("\n\nRewritten Question:\n");
        return sb.toString();
    }
}