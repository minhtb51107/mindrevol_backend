package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // ✅ Executor cho xử lý chat logic (CPU intensive)
    @Bean("chatTaskExecutor")
    public TaskExecutor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
    
    // --- THÊM BEAN MỚI NÀY ĐỂ SỬA LỖI ---
    /**
     * Tạo một executor "bọc" (wrapper) xung quanh chatTaskExecutor.
     * DelegatingSecurityContextAsyncTaskExecutor sẽ tự động sao chép
     * SecurityContext từ luồng web sang luồng async, giải quyết lỗi Access Denied.
     * * ChatAIService sẽ sử dụng bean này thay vì bean cũ.
     */
    @Bean("secureChatTaskExecutor")
    public Executor secureChatTaskExecutor() {
        // This now correctly uses the chatTaskExecutor bean defined above
        return new DelegatingSecurityContextAsyncTaskExecutor((AsyncTaskExecutor) chatTaskExecutor());
    }
    
    // ✅ Executor cho I/O operations (DB, Redis, API calls)
    @Bean("ioTaskExecutor")
    public TaskExecutor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("io-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // ✅ Executor cho embedding processing
    @Bean("embeddingTaskExecutor")
    public TaskExecutor embeddingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("embedding-async-");
        executor.initialize();
        return executor;
    }
    
    /**
     * Định nghĩa một ThreadPoolTaskExecutor để quản lý các luồng cho các tác vụ bất đồng bộ.
     * Điều này cho phép chúng ta kiểm soát số lượng luồng tối đa, hàng đợi, v.v.
     * để tránh làm quá tải hệ thống.
     */
    @Bean(name = "fileIngestionExecutor")
    public Executor fileIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Số luồng cốt lõi luôn hoạt động
        executor.setCorePoolSize(5); 
        // Số luồng tối đa có thể được tạo
        executor.setMaxPoolSize(10); 
        // Số lượng tác vụ có thể chờ trong hàng đợi trước khi bị từ chối
        executor.setQueueCapacity(25); 
        // Tên tiền tố cho các luồng trong pool để dễ dàng nhận dạng trong log
        executor.setThreadNamePrefix("FileIngestion-"); 
        executor.initialize();
        return executor;
    }
}