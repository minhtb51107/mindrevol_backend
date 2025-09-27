package com.example.demo.dto.chat;

import java.util.List;

import lombok.Data;

@Data
public class ChatRequest {
    private List<ChatMessageDTO> messages;
    // getters, setters

	public List<ChatMessageDTO> getMessages() {
		return messages;
	}

	public void setMessages(List<ChatMessageDTO> messages) {
		this.messages = messages;
	}
	
	private boolean regenerate = false; // Thêm trường này với giá trị mặc định là false
}

