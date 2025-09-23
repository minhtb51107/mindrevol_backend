package com.example.demo.evaluation;

import com.example.demo.model.auth.User;
import com.example.demo.model.chat.ChatSession;
import com.example.demo.repository.auth.UserRepository;
import com.example.demo.service.chat.ChatSessionService;
import com.example.demo.service.chat.memory.langchain.LangChainChatMemoryService;
import com.example.demo.service.chat.orchestration.context.RagContext;
import com.example.demo.service.chat.orchestration.pipeline.PipelineManager;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Service
public class EvaluationService {

    @Autowired
    private PipelineManager pipelineManager;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private ChatSessionService chatSessionService;
    @Autowired
    private LangChainChatMemoryService chatMemoryService;
    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void runEvaluation() throws Exception {
        System.out.println("Bắt đầu thực thi pipeline đánh giá...");
        List<EvaluationResult> results = new ArrayList<>();
        List<String[]> goldenDataset = loadGoldenDataset();

        // ✅ SỬA LỖI: Tìm user có sẵn, nếu không có sẽ báo lỗi rõ ràng.
        User evalUser = userRepository.findByEmail("minhbinh51107@gmail.com")
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy người dùng 'eval_user'. Vui lòng tạo user này trong CSDL trước khi chạy đánh giá."
                ));

        System.out.println("Đã tìm thấy user '" + evalUser.getUsername() + "' để bắt đầu đánh giá.");

        for (String[] record : goldenDataset) {
            String question = record[0];
            String expectedAnswer = record[1];

            String sessionIdString = UUID.randomUUID().toString();
            ChatSession session = chatSessionService.findOrCreateSession(sessionIdString, evalUser);
            ChatMemory chatMemory = chatMemoryService.getChatMemory(session.getId());

            RagContext initialContext = RagContext.builder()
                    .initialQuery(question)
                    .session(session)
                    .user(evalUser)
                    .chatMemory(chatMemory)
                    .build();

            RagContext finalContext = pipelineManager.run(initialContext, "default-rag");
            String actualAnswer = finalContext.getReply();

            double score = calculateSemanticSimilarity(expectedAnswer, actualAnswer);
            results.add(new EvaluationResult(question, expectedAnswer, actualAnswer, score));
        }

        double averageScore = results.stream().mapToDouble(EvaluationResult::getScore).average().orElse(0.0);
        results.forEach(System.out::println);
        System.out.printf("\n====================\nAverage Score: %.4f\n====================\n", averageScore);
    }

    // ... các phương thức helper không đổi ...
    private List<String[]> loadGoldenDataset() throws Exception {
        List<String[]> records = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource("evaluation/golden_dataset.csv");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                 for (int i = 0; i < values.length; i++) {
                    values[i] = values[i].replace("\"", "");
                }
                records.add(values);
            }
        }
        return records;
    }

    private double calculateSemanticSimilarity(String text1, String text2) {
         if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }
        Embedding embedding1 = embeddingModel.embed(ensureNotNull(text1, "text1")).content();
        Embedding embedding2 = embeddingModel.embed(ensureNotNull(text2, "text2")).content();
        return cosineSimilarity(embedding1.vector(), embedding2.vector());
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}