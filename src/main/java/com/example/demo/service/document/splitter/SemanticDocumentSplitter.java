package com.example.demo.service.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SemanticDocumentSplitter implements DocumentSplitter {

    private final EmbeddingModel embeddingModel;
    private final double similarityThreshold;
    private static final int MAX_CHUNK_SIZE = 512; // Đặt kích thước chunk tối đa (ví dụ)

    public SemanticDocumentSplitter(EmbeddingModel embeddingModel, double similarityThreshold) {
        this.embeddingModel = embeddingModel;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<TextSegment> finalChunks = new ArrayList<>();
        String text = document.text();

        // 1. Tách văn bản thành các đoạn văn dựa trên dấu xuống dòng kép
        List<String> paragraphs = Arrays.asList(text.split("\\n\\s*\\n"));

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }
            // Nếu đoạn văn đủ nhỏ, coi nó là một chunk
            if (paragraph.length() <= MAX_CHUNK_SIZE) {
                finalChunks.add(TextSegment.from(paragraph, document.metadata()));
            } else {
                // Nếu đoạn văn quá lớn, áp dụng logic chia theo câu
                finalChunks.addAll(splitParagraphIntoSentenceChunks(paragraph, document));
            }
        }

        return finalChunks;
    }

    private List<TextSegment> splitParagraphIntoSentenceChunks(String paragraph, Document document) {
        List<TextSegment> chunks = new ArrayList<>();
        List<String> sentences = splitIntoSentences(paragraph);
        if (sentences.isEmpty()) {
            return chunks;
        }

        List<TextSegment> sentenceSegments = sentences.stream()
                                                      .map(TextSegment::from)
                                                      .collect(Collectors.toList());

        List<Embedding> sentenceEmbeddings = embeddingModel.embedAll(sentenceSegments).content();

        StringBuilder currentChunkContent = new StringBuilder(sentences.get(0));
        Embedding lastEmbedding = sentenceEmbeddings.get(0);

        for (int i = 1; i < sentences.size(); i++) {
            String currentSentence = sentences.get(i);
            Embedding currentEmbedding = sentenceEmbeddings.get(i);

            double similarity = CosineSimilarity.between(lastEmbedding, currentEmbedding);

            // Thêm điều kiện kiểm tra kích thước chunk
            if (similarity >= similarityThreshold && (currentChunkContent.length() + currentSentence.length()) < MAX_CHUNK_SIZE) {
                currentChunkContent.append(" ").append(currentSentence);
            } else {
                chunks.add(TextSegment.from(currentChunkContent.toString(), document.metadata()));
                currentChunkContent = new StringBuilder(currentSentence);
            }
            lastEmbedding = currentEmbedding;
        }

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