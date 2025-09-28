package com.example.demo.service.chat.tools;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WebPageContentFetcher {

    private final OkHttpClient client = new OkHttpClient();

    /**
     * Truy cập một URL và trả về nội dung text đã được làm sạch.
     * @param url URL của trang web.
     * @return Nội dung text của trang.
     */
    public String fetchContent(String url) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Error: Could not fetch content. Status code: " + response.code();
            }
            String html = response.body().string();
            // Sử dụng Jsoup để parse HTML và chỉ lấy text
            Document doc = Jsoup.parse(html);
            return doc.body().text();
        } catch (IOException e) {
            return "Error: Could not fetch content. " + e.getMessage();
        }
    }
}