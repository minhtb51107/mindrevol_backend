//package com.example.demo.service.memory;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//import org.springframework.stereotype.Service;
//
//import com.example.demo.model.auth.User;
//import com.example.demo.model.memory.LongTermMemory;
//import com.example.demo.repository.memory.LongTermMemoryRepository;
//
//import lombok.RequiredArgsConstructor;
//
//@Service
//@RequiredArgsConstructor
//public class LongTermMemoryService {
//
//    private final LongTermMemoryRepository memoryRepo;
//
//    public void saveOrUpdate(User user, String key, String value) {
//        LongTermMemory memory = memoryRepo.findByUserAndKey(user, key)
//            .orElseGet(() -> LongTermMemory.builder()
//                .user(user)
//                .key(key)
//                .build());
//
//        memory.setValue(value);
//        memory.setUpdatedAt(LocalDateTime.now());
//        memoryRepo.save(memory);
//    }
//
//    public Map<String, String> getMemoryMap(User user) {
//        return memoryRepo.findByUser(user).stream()
//            .collect(Collectors.toMap(LongTermMemory::getKey, LongTermMemory::getValue));
//    }
//
//    public Optional<String> get(User user, String key) {
//        return memoryRepo.findByUserAndKey(user, key).map(LongTermMemory::getValue);
//    }
//}
//



