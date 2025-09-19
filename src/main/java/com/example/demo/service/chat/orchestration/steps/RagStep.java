package com.example.demo.service.chat.orchestration.steps;

import com.example.demo.service.chat.orchestration.context.RagContext;

@FunctionalInterface
public interface RagStep {
    /**
     * Thực thi một bước trong RAG pipeline.
     * @param context Đối tượng chứa trạng thái pipeline
     * @return Context đã được cập nhật
     */
    RagContext execute(RagContext context);
}