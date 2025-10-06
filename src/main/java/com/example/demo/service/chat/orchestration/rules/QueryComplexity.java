package com.example.demo.service.chat.orchestration.rules;

/**
 * Defines the complexity levels of a user's query.
 * This helps the Orchestrator decide which model to use.
 */
public enum QueryComplexity {
    /**
     * A simple query that can likely be handled by a basic model (e.g., GPT-3.5).
     * Examples: "what time is it?", "what is the stock price of AAPL?"
     */
    SIMPLE,

    /**
     * A complex query that requires advanced reasoning, multi-step tool use, or nuanced understanding.
     * This should be routed to a more powerful model (e.g., GPT-4).
     * Examples: "Compare the stock performance of Tesla and Google over the last quarter and summarize the key events affecting their prices."
     */
    COMPLEX
}