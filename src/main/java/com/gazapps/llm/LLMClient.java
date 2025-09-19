package com.gazapps.llm;

public interface LLMClient {
    String send(String prompt) throws Exception;
    String getProviderName();
    boolean isHealthy();
}