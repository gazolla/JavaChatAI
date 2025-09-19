package com.gazapps.llm;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class BaseLLMClient implements LLMClient {
    protected final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    protected final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); 
    protected final String apiKey;

    public BaseLLMClient(String apiKey) {
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
    }

    @Override
    public String send(String prompt) throws RuntimeException {
        if (prompt == null || prompt.isBlank()) {
            throw new RuntimeException("Prompt cannot be null or empty");
        }

        try {
            var request = buildRequest(prompt);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "API error: status=%d, body=%s".formatted(response.statusCode(), response.body()));
            }
            return extractAnswer(response.body()).trim();
        } catch (IOException e) {
            throw new RuntimeException("Network error while sending request for prompt: %s".formatted(prompt), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); 
            throw new RuntimeException("Request interrupted for prompt: %s".formatted(prompt), e);
        } catch (IllegalStateException e) {
            throw new RuntimeException("Invalid response format for prompt: %s".formatted(prompt), e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error for prompt: %s".formatted(prompt), e);
        }
    }



    protected abstract HttpRequest buildRequest(String prompt) throws Exception;
    protected abstract String extractAnswer(String jsonResponse) throws Exception;

    @Override
    public boolean isHealthy() {
        try {
            send("Hello");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
