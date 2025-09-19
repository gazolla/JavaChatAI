package com.gazapps.llm;

public class LLMClientFactory {

    public static LLMClient createGroqClient(String apiKey) {
        return new GroqClient(apiKey, "llama-3.3-70b-versatile");
    }

    public static LLMClient createGeminiClient(String apiKey) {
        return new GeminiClient(apiKey, "gemini-1.5-flash");
    }

 
}