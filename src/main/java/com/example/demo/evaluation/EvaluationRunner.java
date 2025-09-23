//package com.example.demo.evaluation;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//
//@Component
//@Profile("eval") // Chỉ chạy khi profile "eval" được kích hoạt
//public class EvaluationRunner implements CommandLineRunner {
//
//    @Autowired
//    private EvaluationService evaluationService;
//
//    @Override
//    public void run(String... args) throws Exception {
//        System.out.println("Starting evaluation pipeline...");
//        evaluationService.runEvaluation();
//        System.out.println("Evaluation finished.");
//        // Kết thúc ứng dụng sau khi chạy xong
//        System.exit(0);
//    }
//}