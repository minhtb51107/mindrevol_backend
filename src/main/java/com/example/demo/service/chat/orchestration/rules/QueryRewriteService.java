package com.example.demo.service.chat.orchestration.rules;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V; // ✅ 1. IMPORT ANNOTATION @V

import java.util.List;

public interface QueryRewriteService {

    // ✅ 2. THAY THẾ {{chat_memory}} BẰNG {{history}}
    @SystemMessage(
        """
        You are a master AI at understanding conversation context. Your goal is to rewrite a user's latest query into a standalone, complete query, preserving the original language.
        
        RULES:
        1. **PRESERVE LANGUAGE**: The rewritten query MUST be in the same language as the user's original 'Final Question'. If the user asks in Vietnamese, you rewrite in Vietnamese.
        2. **CONTEXTUAL ACTIONS**: If the 'Final Question' is a command (e.g., 'translate it', 'summarize', 'dịch nó'), rewrite it as a specific action on the Assistant's PREVIOUS response from the history.
        3. **FOLLOW-UPS**: If the 'Final Question' is a follow-up (e.g., 'what about him?', 'còn Việt Nam thì sao?'), combine it with context from the history to form a full question.
        4. **NO CHANGE**: If the 'Final Question' is already a complete question, return it as is, without any changes.
        5. **OUTPUT**: Your output MUST ONLY be the rewritten query. No explanations, no prefixes.
        
        --- EXAMPLES ---
        History:
        User: Thủ tướng Nhật là ai?
        Assistant: ...
        Final Question: cho tôi biết thêm về ông ấy
        Rewritten Question: Cung cấp thêm thông tin về Thủ tướng Nhật Bản Ishiba Shigeru.

        History:
        User: Tell me about Shigeru Ishiba.
        Assistant: (provides a long English text)
        Final Question: dịch sang tiếng việt
        Rewritten Question: Dịch đoạn văn sau sang tiếng Việt: "(nội dung về Shigeru Ishiba)"
        
        --- CURRENT TASK ---
        Conversation History:
        {{history}}
        """
    )
    // ✅ 3. THAY @MemoryId BẰNG @V("history")
    String rewrite(@V("history") List<ChatMessage> chatHistory, @UserMessage String userMessage);
}