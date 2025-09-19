package com.gazapps.llm;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

public class GeminiClient extends BaseLLMClient {
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private final String model;
    private final String endpointUrl;

    public GeminiClient(String apiKey, String model) {
        super(apiKey);
        this.model = model != null ? model : "gemini-1.5-flash";
        this.endpointUrl = "%s%s:generateContent?key=%s".formatted(BASE_URL, this.model, apiKey);
    }

    @Override
    protected HttpRequest buildRequest(String prompt) throws Exception {
        var jsonBody = objectMapper.writeValueAsString(
            new GeminiRequest(
                List.of(new GeminiRequest.Content(List.of(new GeminiRequest.Part(prompt)))),
                new GeminiRequest.GenerationConfig(0.1, 1000)
            )
        );
        return HttpRequest.newBuilder(URI.create(endpointUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
    }

    @Override
    protected String extractAnswer(String jsonResponse) throws Exception {
        var response = objectMapper.readValue(jsonResponse, GeminiResponse.class);
        var candidates = response.candidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates in response");
        }
        var parts = candidates.get(0).content().parts();
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("No parts in content");
        }
        return parts.get(0).text();
    }
    @Override
    public String getProviderName() {
        return "Google Gemini (%s)".formatted(model);
    }
}

record GeminiRequest(
    List<Content> contents,
    GenerationConfig generationConfig
) {
    record Content(List<Part> parts) {}
    record Part(String text) {}
    record GenerationConfig(double temperature, int maxOutputTokens) {}
}

record GeminiResponse(List<Candidate> candidates) {
    record Candidate(Content content) {}
    record Content(String role, List<Part> parts) {} 
    record Part(String text) {}
}