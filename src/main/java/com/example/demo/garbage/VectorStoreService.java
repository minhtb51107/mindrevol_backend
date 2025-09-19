//package com.example.demo.service.chat.vector;
//
//import com.example.demo.model.chat.ChatMessage;
//import com.example.demo.model.chat.ChatSession;
//import com.example.demo.model.chat.MessageEmbedding;
//import com.example.demo.model.chat.TextChunk;
//import com.example.demo.repository.chat.MessageEmbeddingRepository;
//import com.example.demo.repository.chat.TextChunkRepository;
//import com.example.demo.service.chat.chunking.SmartChunkingService;
//import com.example.demo.service.chat.integration.OpenAIService;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//import java.util.Optional;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class VectorStoreService {
//
//	private final MessageEmbeddingRepository embeddingRepository;
//	private final OpenAIService openAIService;
//	private final JdbcTemplate jdbcTemplate;
//	
//	// Thêm weights cho hybrid search
//    private static final double DEFAULT_SEMANTIC_WEIGHT = 0.6;
//    private static final double DEFAULT_KEYWORD_WEIGHT = 0.4;
//
//    private final SmartChunkingService chunkingService;
//    private final TextChunkRepository chunkRepository;
//    
//    @Transactional
//    public MessageEmbedding saveMessageEmbedding(ChatMessage message, ChatSession session) {
//        try {
//            // Xóa embedding cũ nếu có
//            deleteMessageEmbedding(message.getId());
//            
//            // Chunk message nếu cần
//            List<TextChunk> chunks = chunkingService.chunkMessageIfNeeded(message);
//            chunkRepository.saveAll(chunks);
//            
//            // Tạo embedding cho từng chunk
//            for (TextChunk chunk : chunks) {
//                List<Double> embedding = openAIService.getEmbedding(chunk.getContent());
//                
//                MessageEmbedding messageEmbedding = MessageEmbedding.builder()
//                    .chatMessage(message)
//                    .chatSession(session)
//                    .textChunk(chunk)
//                    .content(chunk.getContent())
//                    .createdAt(LocalDateTime.now())
//                    .updatedAt(LocalDateTime.now())
//                    .build();
//                
//                // ✅ THÊM VALIDATION TRƯỚC KHI LƯU
//                validateMessageEmbedding(messageEmbedding);
//                
//                messageEmbedding.setEmbeddingFromList(embedding);
//                
//                // ✅ SET CÁC TRƯỜNG METADATA VỚI ĐỘ DÀI AN TOÀN
//                messageEmbedding.setSenderType(getSafeSenderType(message.getSender()));
//                messageEmbedding.setDetectedTopic(getSafeTopic(detectTopicForContent(chunk.getContent())));
//                messageEmbedding.setMessageTimestamp(message.getTimestamp());
//                
//                embeddingRepository.save(messageEmbedding);
//            }
//            
//            return null;
//        } catch (Exception e) {
//            log.error("Lỗi khi lưu embedding cho message {}: {}", message.getId(), e.getMessage());
//            throw new RuntimeException("Không thể lưu embedding", e);
//        }
//    }
//
//    // ✅ CÁC PHƯƠNG THỨC HELPER ĐẢM BẢO ĐỘ DÀI AN TOÀN
//    private String getSafeSenderType(String sender) {
//        if (sender == null) return "unknown";
//        String normalized = sender.toLowerCase();
//        if (normalized.contains("user")) return "user";
//        if (normalized.contains("assistant")) return "assistant";
//        if (normalized.contains("system")) return "system";
//        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
//    }
//
//    private String getSafeTopic(String topic) {
//        if (topic == null) return "general";
//        return topic.length() > 100 ? topic.substring(0, 100) : topic;
//    }
//
//    private String detectTopicForContent(String content) {
//        if (content == null || content.length() < 10) return "general";
//        
//        // Simple topic detection
//        String lowerContent = content.toLowerCase();
//        if (lowerContent.contains("java") || lowerContent.contains("code") || lowerContent.contains("program")) {
//            return "programming";
//        }
//        if (lowerContent.contains("weather") || lowerContent.contains("thời tiết")) {
//            return "weather";
//        }
//        if (lowerContent.contains("music") || lowerContent.contains("nhạc")) {
//            return "music";
//        }
//        if (lowerContent.contains("food") || lowerContent.contains("đồ ăn")) {
//            return "food";
//        }
//        
//        return "general";
//    }
//    
//    public List<ChatMessage> findSimilarMessages(Long sessionId, String query, int limit) {
//        // Tìm các chunk tương tự với context mở rộng
//        List<MessageEmbedding> similarChunks = findSimilarChunksWithContext(sessionId, query, limit * 2, 1); // +1 chunk context
//        
//        // Nhóm theo message gốc và trả về
//        return similarChunks.stream()
//            .map(me -> me.getChatMessage())
//            .distinct()
//            .limit(limit)
//            .collect(Collectors.toList());
//    }
//    
//    private List<MessageEmbedding> findSimilarChunks(Long sessionId, String query, int limit) {
//        try {
//            List<Double> queryEmbedding = openAIService.getEmbedding(query);
//            String embeddingString = convertEmbeddingToString(queryEmbedding);
//            
//            List<Object[]> results = embeddingRepository.findSimilarChunksWithThreshold(
//                embeddingString, sessionId, 0.65, limit);
//            
//            return processChunkResults(results);
//        } catch (Exception e) {
//            log.error("Lỗi khi tìm chunk tương tự: {}", e.getMessage(), e);
//            return List.of();
//        }
//    }
//    
// // ✅ TÌM CHUNKS VỚI CONTEXT MỞ RỘNG (bao gồm các chunks liền kề)
//    public List<MessageEmbedding> findSimilarChunksWithContext(Long sessionId, String query, int limit, int contextChunks) {
//        try {
//            // Tìm chunks tương tự ban đầu
//            List<MessageEmbedding> similarChunks = findSimilarChunks(sessionId, query, limit * 2);
//            
//            if (similarChunks.isEmpty()) {
//                return similarChunks;
//            }
//            
//            // Lấy các chunks liền kề để cung cấp context
//            List<MessageEmbedding> chunksWithContext = new ArrayList<>();
//            
//            for (MessageEmbedding chunk : similarChunks) {
//                if (chunk.getTextChunk() != null) {
//                    chunksWithContext.add(chunk);
//                    
//                    // Thêm các chunks trước và sau
//                    addAdjacentChunks(chunksWithContext, chunk.getTextChunk(), contextChunks);
//                    
//                    if (chunksWithContext.size() >= limit) {
//                        break;
//                    }
//                }
//            }
//            
//            return chunksWithContext.stream()
//                .distinct()
//                .limit(limit)
//                .collect(Collectors.toList());
//                
//        } catch (Exception e) {
//            log.error("Lỗi khi tìm chunks với context: {}", e.getMessage(), e);
//            return findSimilarChunks(sessionId, query, limit);
//        }
//    }
//
//    // ✅ THÊM CÁC CHUNKS LIỀN KỀ
//    private void addAdjacentChunks(List<MessageEmbedding> resultList, TextChunk centerChunk, int contextChunks) {
//        if (centerChunk.getOriginalMessage() == null) {
//            return;
//        }
//        
//        Long messageId = centerChunk.getOriginalMessage().getId();
//        int centerIndex = centerChunk.getChunkIndex();
//        
//        // Lấy tất cả chunks của message
//        List<TextChunk> allChunks = chunkRepository.findByMessageIdOrdered(messageId);
//        
//        if (allChunks.isEmpty()) {
//            return;
//        }
//        
//        // Thêm chunks trước
//        for (int i = 1; i <= contextChunks; i++) {
//            int prevIndex = centerIndex - i;
//            if (prevIndex >= 0 && prevIndex < allChunks.size()) {
//                TextChunk prevChunk = allChunks.get(prevIndex);
//                Optional<MessageEmbedding> prevEmbedding = embeddingRepository.findByTextChunkId(prevChunk.getId());
//                prevEmbedding.ifPresent(resultList::add);
//            }
//        }
//        
//        // Thêm chunks sau
//        for (int i = 1; i <= contextChunks; i++) {
//            int nextIndex = centerIndex + i;
//            if (nextIndex >= 0 && nextIndex < allChunks.size()) {
//                TextChunk nextChunk = allChunks.get(nextIndex);
//                Optional<MessageEmbedding> nextEmbedding = embeddingRepository.findByTextChunkId(nextChunk.getId());
//                nextEmbedding.ifPresent(resultList::add);
//            }
//        }
//    }
//    
// // ✅ PHƯƠNG THỨC XỬ LÝ KẾT QUẢ CHUNK TỪ NATIVE QUERY
//    private List<MessageEmbedding> processChunkResults(List<Object[]> results) {
//        List<MessageEmbedding> similarChunks = new ArrayList<>();
//
//        for (Object[] result : results) {
//            try {
//                // result[0] là MessageEmbedding, result[1] là similarity score
//                if (result.length >= 2 && result[0] instanceof MessageEmbedding) {
//                    MessageEmbedding me = (MessageEmbedding) result[0];
//                    Double similarity = (Double) result[1];
//                    
//                    // Set similarity score for ranking/analysis
//                    me.setSimilarityScore(similarity);
//                    
//                    similarChunks.add(me);
//                    log.debug("Found similar chunk: {} (similarity: {}, chunk type: {})", 
//                             me.getContent(), similarity, 
//                             me.getTextChunk() != null ? me.getTextChunk().getChunkType() : "unknown");
//                }
//            } catch (Exception e) {
//                log.warn("Error processing chunk result: {}", e.getMessage());
//            }
//        }
//
//        return similarChunks;
//    }
//
//	public List<ChatMessage> findHybridMessages(Long sessionId, String query, int limit) {
//        return findHybridMessages(sessionId, query, limit, 
//                                DEFAULT_SEMANTIC_WEIGHT, DEFAULT_KEYWORD_WEIGHT);
//    }
//    
//    public List<ChatMessage> findHybridMessages(Long sessionId, String query, int limit,
//                                              double semanticWeight, double keywordWeight) {
//        try {
//            List<Double> queryEmbedding = openAIService.getEmbedding(query);
//            String embeddingString = convertEmbeddingToString(queryEmbedding);
//            
//            // Chuẩn hóa query cho full-text search
//            String normalizedQuery = normalizeQueryForFTS(query);
//            
//            List<Object[]> results = embeddingRepository.findHybridResults(
//                embeddingString, normalizedQuery, sessionId, 
//                semanticWeight, keywordWeight, limit
//            );
//            
//            return processNativeQueryResults(results);
//        } catch (Exception e) {
//            log.error("Lỗi hybrid search: {}", e.getMessage(), e);
//            // Fallback to semantic search
//            return findSimilarMessagesWithThreshold(sessionId, query, limit, 0.5);
//        }
//    }
//    
//    private String normalizeQueryForFTS(String query) {
//        if (query == null || query.isBlank()) return "";
//        
//        // Chuẩn hóa query cho PostgreSQL tsquery
//        return query.toLowerCase()
//                  .replaceAll("[^a-z0-9\\s]", " ") // Remove special chars
//                  .replaceAll("\\s+", " ")         // Collapse multiple spaces
//                  .trim()
//                  .replace(" ", " & ");           // AND operator for tsquery
//    }
//    
//    // Thêm phương thức BM25-only search
//    public List<ChatMessage> findKeywordMessages(Long sessionId, String query, int limit) {
//        try {
//            String normalizedQuery = normalizeQueryForFTS(query);
//            List<Object[]> results = embeddingRepository.findByKeywordSearch(
//                normalizedQuery, sessionId, limit
//            );
//            
//            return processNativeQueryResults(results);
//        } catch (Exception e) {
//            log.error("Lỗi keyword search: {}", e.getMessage(), e);
//            return List.of();
//        }
//    }
//
//	// Tìm similar messages không có threshold
//	// ✅ THÊM CACHE CHO EMBEDDING REQUESTS
//	private final Cache<String, List<Double>> embeddingCache = Caffeine.newBuilder().maximumSize(1000)
//			.expireAfterWrite(10, TimeUnit.MINUTES).build();
//
//
//	// ✅ THÊM PHƯƠNG THỨC CHUYỂN ĐỔI EMBEDDING SANG STRING
//	private String convertEmbeddingToString(List<Double> embedding) {
//		if (embedding == null || embedding.isEmpty()) {
//			return "[]";
//		}
//
//		StringBuilder sb = new StringBuilder("[");
//		for (int i = 0; i < embedding.size(); i++) {
//			if (i > 0) {
//				sb.append(",");
//			}
//			sb.append(embedding.get(i));
//		}
//		sb.append("]");
//		return sb.toString();
//	}
//
//	public List<ChatMessage> findSimilarMessagesWithThreshold(Long sessionId, String query, int limit,
//			double threshold) {
//		try {
//			List<Double> queryEmbedding = openAIService.getEmbedding(query);
//			String embeddingString = convertEmbeddingToString(queryEmbedding);
//
//			List<Object[]> results = embeddingRepository.findSimilarMessagesWithThreshold(embeddingString, sessionId,
//					threshold, limit);
//
//// ✅ XỬ LÝ ĐÚNG KẾT QUẢ TỪ NATIVE QUERY
//			return processNativeQueryResults(results);
//
//		} catch (Exception e) {
//			log.error("Lỗi khi tìm message tương tự: {}", e.getMessage(), e);
//			return List.of();
//		}
//	}
//
//// ✅ PHƯƠNG THỨC XỬ LÝ KẾT QUẢ NATIVE QUERY
//	private List<ChatMessage> processNativeQueryResults(List<Object[]> results) {
//	    List<ChatMessage> similarMessages = new ArrayList<>();
//
//	    for (Object[] result : results) {
//	        try {
//	            if (result.length >= 2 && result[0] instanceof MessageEmbedding) {
//	                MessageEmbedding me = (MessageEmbedding) result[0];
//	                Double similarity = (Double) result[1];
//
//	                if (me.getChatMessage() != null) {
//	                    // Set similarity score cho ChatMessage
//	                    me.getChatMessage().setSimilarityScore(similarity);
//	                    similarMessages.add(me.getChatMessage());
//	                    
//	                    log.debug("Found similar message: {} (similarity: {})", 
//	                             me.getChatMessage().getContent(), similarity);
//	                }
//	            }
//	        } catch (Exception e) {
//	            log.warn("Error processing query result: {}", e.getMessage());
//	        }
//	    }
//
//	    return similarMessages;
//	}
//
//	@Transactional
//	public void deleteMessageEmbedding(Long messageId) {
//		embeddingRepository.deleteByChatMessage_Id(messageId);
//	}
//
//	@Transactional
//	public void deleteSessionEmbeddings(Long sessionId) {
//		List<MessageEmbedding> embeddings = embeddingRepository.findByChatSession_Id(sessionId);
//		embeddingRepository.deleteAll(embeddings);
//	}
//
//	// Kiểm tra xem message đã có embedding chưa
//	public boolean hasEmbedding(Long messageId) {
//		return embeddingRepository.existsByChatMessage_Id(messageId);
//	}
//
//	// Lấy số lượng embeddings của session
//	public long countSessionEmbeddings(Long sessionId) {
//		return embeddingRepository.countByChatSession_Id(sessionId);
//	}
//
//	// Lấy embedding theo messageId
//	public Optional<MessageEmbedding> getEmbeddingByMessageId(Long messageId) {
//		return embeddingRepository.findByChatMessage_Id(messageId);
//	}
//	
//	   // ✅ FIND WITH PRE-FILTERING
//    public List<ChatMessage> findSimilarMessagesWithFilters(Long sessionId, String query, int limit, 
//                                                          String senderType, String topic, 
//                                                          LocalDateTime startTime, LocalDateTime endTime) {
//        try {
//            List<Double> queryEmbedding = openAIService.getEmbedding(query);
//            String embeddingString = convertEmbeddingToString(queryEmbedding);
//            
//            List<Object[]> results;
//            
//            if (senderType != null && topic != null && startTime != null) {
//                // Combined filtering
//                results = embeddingRepository.findSimilarMessagesWithMultipleFilters(
//                    embeddingString, sessionId, senderType, startTime, topic, limit
//                );
//            } else if (senderType != null) {
//                // Filter by sender only
//                results = embeddingRepository.findSimilarMessagesBySender(
//                    embeddingString, sessionId, senderType, limit
//                );
//            } else if (topic != null) {
//                // Filter by topic only
//                results = embeddingRepository.findSimilarMessagesByTopic(
//                    embeddingString, sessionId, topic, limit
//                );
//            } else if (startTime != null && endTime != null) {
//                // Filter by time range
//                results = embeddingRepository.findSimilarMessagesByTimeRange(
//                    embeddingString, sessionId, startTime, endTime, limit
//                );
//            } else {
//                // No filtering
//                results = embeddingRepository.findSimilarMessagesWithThreshold(
//                    embeddingString, sessionId, 0.65, limit
//                );
//            }
//            
//            return processNativeQueryResults(results);
//        } catch (Exception e) {
//            log.error("Lỗi khi tìm message với filters: {}", e.getMessage(), e);
//            return List.of();
//        }
//    }
//    
//    // ✅ FIND USER MESSAGES ABOUT TOPIC FROM LAST WEEK
//    public List<ChatMessage> findUserMessagesAboutTopicFromLastWeek(Long sessionId, String query, 
//                                                                   String topic, int limit) {
//        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
//        return findSimilarMessagesWithFilters(sessionId, query, limit, "user", topic, oneWeekAgo, LocalDateTime.now());
//    }
//    
//    private void validateMessageEmbedding(MessageEmbedding embedding) {
//        if (embedding.getSenderType() != null && embedding.getSenderType().length() > 50) {
//            embedding.setSenderType(embedding.getSenderType().substring(0, 50));
//            log.warn("Truncated sender_type to 50 characters");
//        }
//        
//        if (embedding.getDetectedTopic() != null && embedding.getDetectedTopic().length() > 100) {
//            embedding.setDetectedTopic(embedding.getDetectedTopic().substring(0, 100));
//            log.warn("Truncated detected_topic to 100 characters");
//        }
//        
//        if (embedding.getContent() != null && embedding.getContent().length() > 10000) {
//            embedding.setContent(embedding.getContent().substring(0, 10000));
//            log.warn("Truncated content to 10000 characters");
//        }
//    }
//}

