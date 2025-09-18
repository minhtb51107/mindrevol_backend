package com.example.demo.dto.auth;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private Integer age;
    private String verificationCode;
}


