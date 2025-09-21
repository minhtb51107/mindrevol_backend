package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling; // ✅ Giữ lại

// ✅ THÊM TẤT CẢ CÁC IMPORT NÀY
//import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
//import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
//import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
//import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
//import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
//// ✅ DÒNG MỚI BẠN CẦN THÊM VÀO IMPORT
//import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;


@SpringBootApplication(exclude = {
//    OpenAiChatAutoConfiguration.class,
//    OpenAiEmbeddingAutoConfiguration.class,
//    OpenAiAudioSpeechAutoConfiguration.class,
//    OpenAiAudioTranscriptionAutoConfiguration.class,
//    OpenAiImageAutoConfiguration.class,
//    // ✅ DÒNG MỚI BẠN CẦN THÊM VÀO EXCLUDE
//    OpenAiModerationAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@EnableRetry
public class Ban1Application {

	public static void main(String[] args) {
		SpringApplication.run(Ban1Application.class, args);
	}

}
