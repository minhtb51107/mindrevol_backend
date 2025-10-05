package com.example.demo.config.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import dev.langchain4j.data.embedding.Embedding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmbeddingDeserializer extends JsonDeserializer<Embedding> {

    @Override
    public Embedding deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        // Đọc mảng JSON thành một mảng float[]
        float[] vector = p.readValueAs(float[].class);
        // Tạo lại đối tượng Embedding từ mảng float[]
        return new Embedding(vector);
    }
}