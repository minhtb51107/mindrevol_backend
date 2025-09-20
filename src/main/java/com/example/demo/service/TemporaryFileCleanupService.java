package com.example.demo.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.repository.chat.ChatSessionRepository;
import com.example.demo.repository.chat.KnowledgeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//src/main/java/com/example/demo/service/TemporaryFileCleanupService.java

@Service
@RequiredArgsConstructor
@Slf4j
public class TemporaryFileCleanupService {

 private final KnowledgeRepository knowledgeRepository;
 private final ChatSessionRepository chatSessionRepository;

 /**
  * Tác vụ này chạy vào lúc 2 giờ sáng mỗi ngày để dọn dẹp.
  */
 @Scheduled(cron = "0 0 2 * * ?")
 public void cleanupOrphanedTemporaryFiles() {
     log.info("Bắt đầu tác vụ dọn dẹp file tạm thời mồ côi.");

     // 1. Lấy danh sách tất cả sessionId của file tạm thời từ vector store
     List<Long> sessionIdsInVectorStore = knowledgeRepository.findDistinctSessionIdsForTempFiles();
     if (sessionIdsInVectorStore.isEmpty()) {
         log.info("Không tìm thấy file tạm thời nào trong vector store. Kết thúc.");
         return;
     }

     // 2. Lấy danh sách tất cả sessionId đang tồn tại trong CSDL quan hệ
     List<Long> existingSessionIds = chatSessionRepository.findAllIds();

     // 3. Xác định các sessionId mồ côi
     List<Long> orphanedSessionIds = sessionIdsInVectorStore.stream()
             .filter(id -> !existingSessionIds.contains(id))
             .collect(Collectors.toList());

     if (orphanedSessionIds.isEmpty()) {
         log.info("Không có file tạm thời mồ côi nào cần dọn dẹp. Kết thúc.");
         return;
     }

     // 4. Xóa dữ liệu của các session mồ côi
     log.warn("Phát hiện {} session mồ côi. Bắt đầu xóa...", orphanedSessionIds.size());
     int totalDeletedCount = 0;
     for (Long sessionId : orphanedSessionIds) {
         log.info("Đang xóa file của session mồ côi: {}", sessionId);
         // Lưu ý: hàm này không cần userId vì session đã không còn tồn tại
         int deletedCount = knowledgeRepository.deleteTemporaryDocumentsBySession(sessionId);
         totalDeletedCount += deletedCount;
     }

     log.info("Hoàn tất tác vụ dọn dẹp. Đã xóa tổng cộng {} chunks từ {} session mồ côi.", totalDeletedCount, orphanedSessionIds.size());
 }
}