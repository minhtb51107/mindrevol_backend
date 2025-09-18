//package com.example.demo.test;
//
//import com.example.demo.analyzer.IntentAnalyzer;
//import com.example.demo.analyzer.VectorIntentRetriever;
//import com.example.demo.model.ai.IntentSample;
//import com.example.demo.service.ai.embedding.EmbedderService;
//import com.example.demo.service.ai.embedding.EmbedderServiceImpl;
//import com.example.demo.service.ai.embedding.ResilientEmbedderService;
//import com.example.demo.service.ai.orchestrator.PromptOrchestratorService;
//import com.example.demo.store.IntentSampleStore;
//
//import java.util.Arrays;
//import java.util.List;
//
//
//public class IntentApp {
//    public static void main(String[] args) {
//        EmbedderService rawEmbedder = new EmbedderServiceImpl();
//        EmbedderService embedderService = new ResilientEmbedderService(rawEmbedder);
//
//        IntentSampleStore store = new IntentSampleStore();
//        store.addSample(new IntentSample("greeting", "xin chào", embedderService.embed("xin chào")));
//        store.addSample(new IntentSample("goodbye", "tạm biệt", embedderService.embed("tạm biệt")));
//        store.addSample(new IntentSample("question", "làm sao để đăng ký?", embedderService.embed("làm sao để đăng ký?")));
//
//        IntentAnalyzer analyzer = new VectorIntentRetriever(embedderService, store);
//        PromptOrchestratorService orchestrator = new PromptOrchestratorService(analyzer);
//
//        String userInput = "chào bạn";
//        String response = orchestrator.handleInput(userInput);
//
//        System.out.println(response);
//    }
//}