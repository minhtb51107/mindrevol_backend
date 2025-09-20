package com.example.demo.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfoDTO {
    // Chúng ta chỉ cần tên file để hiển thị
    private String fileName;
}