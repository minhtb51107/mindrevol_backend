// src/main/java/com/example/demo/service/chat/tools/TimeTool.java
package com.example.demo.service.chat.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TimeTool {

    @Tool("Get the current date and time.")
    public String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
        return now.format(formatter);
    }
}