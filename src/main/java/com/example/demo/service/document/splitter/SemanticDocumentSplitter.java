package com.example.demo.service.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors; // ✅ THÊM IMPORT

public class SemanticDocumentSplitter implements DocumentSplitter {

    private final EmbeddingModel embeddingModel;
    private final double similarityThreshold;

    public SemanticDocumentSplitter(EmbeddingModel embeddingModel, double similarityThreshold) {
        this.embeddingModel = embeddingModel;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<TextSegment> chunks = new ArrayList<>();
        String text = document.text();

        // 1. Tách văn bản thành các câu
        List<String> sentences = splitIntoSentences(text);
        if (sentences.isEmpty()) {
            return chunks;
        }

        // ✅ SỬA LỖI: Chuyển đổi List<String> thành List<TextSegment>
        List<TextSegment> sentenceSegments = sentences.stream()
                                                      .map(TextSegment::from)
                                                      .collect(Collectors.toList());

        // 2. Tạo embedding cho mỗi câu từ List<TextSegment>
        List<Embedding> sentenceEmbeddings = embeddingModel.embedAll(sentenceSegments).content();

        // 3. Nhóm các câu thành chunk dựa trên độ tương đồng
        StringBuilder currentChunkContent = new StringBuilder(sentences.get(0));
        Embedding lastEmbedding = sentenceEmbeddings.get(0);

        for (int i = 1; i < sentences.size(); i++) {
            String currentSentence = sentences.get(i);
            Embedding currentEmbedding = sentenceEmbeddings.get(i);

            // 4. So sánh độ tương đồng (cosine similarity)
            double similarity = CosineSimilarity.between(lastEmbedding, currentEmbedding);

            if (similarity >= similarityThreshold) {
                currentChunkContent.append(" ").append(currentSentence);
            } else {
                chunks.add(TextSegment.from(currentChunkContent.toString(), document.metadata()));
                currentChunkContent = new StringBuilder(currentSentence);
            }
            lastEmbedding = currentEmbedding;
        }

        // Thêm chunk cuối cùng vào danh sách
        chunks.add(TextSegment.from(currentChunkContent.toString(), document.metadata()));

        return chunks;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
}