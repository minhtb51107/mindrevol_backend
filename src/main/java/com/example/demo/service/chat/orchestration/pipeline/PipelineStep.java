package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.service.chat.orchestration.context.RagContext;

public interface PipelineStep<T> { // Sử dụng Generics
    /**
     * Tên định danh của step, dùng trong file config.
     */
    String getStepName();

    /**
     * Mỗi step sẽ nhận context, xử lý và trả về một đối tượng kết quả.
     */
    T execute(RagContext context);
}