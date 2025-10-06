//package com.example.demo.test;
//
//import org.openqa.selenium.By;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.WebElement;
//import org.openqa.selenium.chrome.ChromeDriver;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//public class ChatE2ETest {
//    public static void main(String[] args) throws InterruptedException {
//        // 1. Thiết lập WebDriver
//        System.setProperty("webdriver.chrome.driver", "path/to/chromedriver.exe");
//        WebDriver driver = new ChromeDriver();
//
//        try {
//            // 2. Mở trang web chat
//            driver.get("http://URL_CUA_FRONTEND");
//
//            // 3. Tìm các phần tử UI
//            WebElement messageInput = driver.findElement(By.id("chat-input-box"));
//            WebElement sendButton = driver.findElement(By.id("send-button"));
//
//            // 4. Giả lập hành vi người dùng
//            messageInput.sendKeys("Thời tiết ở Hà Nội thế nào?");
//            sendButton.click();
//
//            // Chờ backend và frontend xử lý
//            Thread.sleep(5000); // Lưu ý: trong thực tế dùng WebDriverWait
//
//            // 5. Tìm phần tử chứa câu trả lời và kiểm tra
//            WebElement lastMessage = driver.findElement(By.cssSelector(".message-list .message:last-child .content"));
//            String botResponse = lastMessage.getText();
//
//            System.out.println("Bot response: " + botResponse);
//            assertTrue(botResponse.contains("Hà Nội"));
//
//        } finally {
//            // Đóng trình duyệt
//            driver.quit();
//        }
//    }
//}