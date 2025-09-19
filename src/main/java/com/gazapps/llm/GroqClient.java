package com.gazapps.llm;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

public class GroqClient extends BaseLLMClient {
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final String model;

    public GroqClient(String apiKey, String model) {
        super(apiKey);
        this.model = model != null ? model : "llama-3.3-70b-versatile";
    }

    @Override
    protected HttpRequest buildRequest(String prompt) throws Exception {
        var jsonBody = objectMapper.writeValueAsString(
            new GroqRequest(
                model,
                List.of(new GroqRequest.Message("user", prompt)),
                1000,
                0.1
            )
        );
        return HttpRequest.newBuilder(URI.create(GROQ_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer %s".formatted(apiKey)) 
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
    }

    @Override
    protected String extractAnswer(String jsonResponse) throws Exception {
        var response = objectMapper.readValue(jsonResponse, GroqResponse.class);
        var choices = response.choices();
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in response");
        }
        return choices.get(0).message().content();
    }

    @Override
    public String getProviderName() {
        return "Groq (%s)".formatted(model);
    }
}

record GroqRequest(
    String model,
    List<Message> messages,
    int max_tokens,
    double temperature
) {
    record Message(String role, String content) {}
}

record GroqResponse(List<Choice> choices) {
    record Choice(Message message) {}
    record Message(String content) {}
}