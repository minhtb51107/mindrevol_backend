package com.example.demo.garbage;

import java.util.List;
import java.util.Map;

public abstract class AICompletionService {
	public abstract String getChatCompletion(List<Map<String, String>> messages, String model, int maxTokens);
}
