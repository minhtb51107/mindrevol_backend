//package com.example.demo.task;
//
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.model.chat.HierarchicalMemory;
//import com.example.demo.repository.chat.ChatSessionRepository;
//import com.example.demo.repository.chat.memory.HierarchicalMemoryRepository;
//import com.example.demo.service.chat.memory.HierarchicalMemoryManager;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class HierarchicalMemoryTask {
//    
//    private final ChatSessionRepository chatSessionRepository;
//    private final HierarchicalMemoryManager hierarchicalMemoryManager;
//    private final HierarchicalMemoryRepository hierarchicalMemoryRepository;
//    
//    @Scheduled(fixedDelay = 300000) // 5 phút
//    public void rebuildHierarchies() {
//        log.info("Bắt đầu xây dựng lại hierarchical memories...");
//        
//        List<ChatSession> sessions = chatSessionRepository.findAll();
//        
//        for (ChatSession session : sessions) {
//            try {
//                // Chỉ rebuild nếu có summary mới
//                if (hasNewSummaries(session)) {
//                    hierarchicalMemoryManager.buildFullHierarchy(session);
//                    log.debug("Đã xây dựng hierarchy cho session {}", session.getId());
//                }
//            } catch (Exception e) {
//                log.error("Lỗi khi xây dựng hierarchy cho session {}: {}", session.getId(), e.getMessage());
//            }
//        }
//    }
//
// // Trong HierarchicalMemoryManager
//    private boolean hasNewSummaries(ChatSession session) {
//        Optional<HierarchicalMemory> latestSummary = hierarchicalMemoryRepository
//            .findTopByChatSessionOrderByUpdatedAtDesc(session);
//        
//        if (latestSummary.isEmpty()) return false;
//        
//        // Kiểm tra nếu có summary được cập nhật trong 10 phút gần đây
//        return latestSummary.get().getUpdatedAt()
//            .isAfter(LocalDateTime.now().minusMinutes(10));
//    }
//}