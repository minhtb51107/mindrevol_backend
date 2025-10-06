//package com.example.demo.test;
//
//import io.restassured.RestAssured;
//import org.junit.jupiter.api.Test;
//import static org.hamcrest.Matchers.containsString;
//
//public class ChatApiTest {
//    @Test
//    public void whenSendMessage_thenReceiveValidResponse() {
//        String requestBody = "{\"message\": \"Chào bạn\"}";
//
//        RestAssured.given()
//            .baseUri("http://localhost:8080")
//            .contentType("application/json")
//            .body(requestBody)
//        .when()
//            .post("/api/chat/123") // Giả sử sessionId là 123
//        .then()
//            .statusCode(200)
//            .body("answer", containsString("Chào bạn")); // Kiểm tra câu trả lời có chứa "Chào bạn"
//    }
//}