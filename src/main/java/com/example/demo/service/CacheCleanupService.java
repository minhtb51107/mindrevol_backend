//package com.example.demo.service;
//
//import jakarta.annotation.PostConstruct;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.connection.RedisConnection;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.stereotype.Service;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//@Service
//public class CacheCleanupService {
//
//    @Autowired
//    private RedisConnectionFactory redisConnectionFactory;
//
//    /**
//     * Phương thức này sẽ tự động chạy MỘT LẦN DUY NHẤT sau khi ứng dụng khởi động.
//     * Nó sẽ thực hiện lệnh FLUSHALL để xóa toàn bộ dữ liệu cache trên Redis.
//     */
//    @PostConstruct
//    public void clearAllRedisCacheOnStartup() {
//        log.warn("!!! --- DANGER: EXECUTING FLUSHALL ON REDIS --- !!!");
//        log.warn("!!! --- This should only run once to clear poisoned cache. --- !!!");
//        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
//            connection.serverCommands().flushAll();
//            log.info("!!! --- Successfully cleared all Redis cache. --- !!!");
//        } catch (Exception e) {
//            log.error("!!! --- FAILED to clear Redis cache. --- !!!", e);
//        }
//    }
//}