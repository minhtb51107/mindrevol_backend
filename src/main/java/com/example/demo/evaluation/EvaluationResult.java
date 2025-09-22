package com.example.demo.evaluation;

public class EvaluationResult {
    private final String question;
    private final String expectedAnswer;
    private final String actualAnswer;
    private final double score;

    // Constructor, Getters
    public EvaluationResult(String question, String expectedAnswer, String actualAnswer, double score) {
        this.question = question;
        this.expectedAnswer = expectedAnswer;
        this.actualAnswer = actualAnswer;
        this.score = score;
    }

    @Override
    public String toString() {
        return String.format("Question: %s\nExpected: %s\nActual: %s\nScore: %.2f\n-----------------",
                             question, expectedAnswer, actualAnswer, score);
    }

    public double getScore() {
        return score;
    }
}