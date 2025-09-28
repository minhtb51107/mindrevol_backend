package com.example.demo.repository.chat;

import com.example.demo.model.chat.QuestionAnswerCache;

// Interface này cho phép Spring Data JPA tự động map kết quả từ native query
public interface QuestionAnswerCacheProjection {
    QuestionAnswerCache getQuestionAnswerCache();
    double getDistance();
}