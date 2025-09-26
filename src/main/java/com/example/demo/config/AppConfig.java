package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

	// Trong một class @Configuration nào đó, ví dụ AppConfig.java
	@Bean
	public RestTemplate restTemplate() {
	    return new RestTemplate();
	}
}