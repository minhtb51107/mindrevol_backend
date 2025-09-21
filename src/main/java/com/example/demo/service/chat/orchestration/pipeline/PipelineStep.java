package com.example.demo.service.chat.orchestration.pipeline;

import com.example.demo.service.chat.orchestration.context.RagContext;

public interface PipelineStep {
    /**
     * Tên định danh của step, dùng trong file config.
     */
    String getStepName();

    /**
     * Mỗi step sẽ nhận context, xử lý và cập nhật lại context.
     */
    RagContext execute(RagContext context);
}