package com.example.demo.config.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;

public class EmbeddingSerializer extends JsonSerializer<Embedding> {

    @Override
    public void serialize(Embedding embedding, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (embedding == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartArray();
        for (float value : embedding.vector()) {
            gen.writeNumber(value);
        }
        gen.writeEndArray();
    }
}