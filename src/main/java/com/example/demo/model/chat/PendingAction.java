package com.example.demo.model.chat;

public enum PendingAction {
    /**
     * Không có hành động nào đang chờ.
     */
    NONE,

    /**
     * Chờ người dùng xác nhận việc tìm kiếm trên Internet.
     */
    AWAITING_INTERNET_SEARCH_CONFIRMATION
}