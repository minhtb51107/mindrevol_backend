package com.example.demo.dto.chat;

import java.util.List;

public class ChatRequest {
    private List<ChatMessageDTO> messages;
    // getters, setters

	public List<ChatMessageDTO> getMessages() {
		return messages;
	}

	public void setMessages(List<ChatMessageDTO> messages) {
		this.messages = messages;
	}
}

