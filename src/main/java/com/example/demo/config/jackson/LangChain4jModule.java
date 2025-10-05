package com.example.demo.config.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.data.embedding.Embedding;

public class LangChain4jModule extends SimpleModule {
    public LangChain4jModule() {
        super("LangChain4jModule");
        // Đăng ký serializer (Object -> JSON)
        addSerializer(Embedding.class, new EmbeddingSerializer());
        
        // ✅ THÊM DÒNG NÀY: Đăng ký deserializer (JSON -> Object)
        addDeserializer(Embedding.class, new EmbeddingDeserializer());
    }
}