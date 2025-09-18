package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value; 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;

@Configuration
public class VectorStoreConfig {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        
        String urlWithoutPrefix = dbUrl.substring("jdbc:postgresql://".length());
        String[] hostPortDb = urlWithoutPrefix.split("/");
        String[] hostPort = hostPortDb[0].split(":");
        
        String host = hostPort[0];
        Integer port = Integer.parseInt(hostPort[1]);
        String database = hostPortDb[1];

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(dbUsername)
                .password(dbPassword)
                .table("message_embeddings") 
                .dimension(1536) 
                .useIndex(true)         // ✅ SỬA LỖI: Bật lại Index
                .indexListSize(100)   // ✅ Thêm lại tham số (bắt buộc khi useIndex là true)
                .build();
    }
}