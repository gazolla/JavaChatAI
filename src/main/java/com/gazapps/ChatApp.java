package com.gazapps;

import com.gazapps.inference.MultiToolOrchestrator;
import com.gazapps.llm.LLMClient;
import com.gazapps.llm.LLMClientFactory;
import com.gazapps.mcp.MCPService;
import com.gazapps.ui.ChatInterface;

public class ChatApp {
    public static void main(String[] args) {
        try {
            System.out.println("Starting AI Chat Assistant...");
            
            MCPService mcpService = new MCPService();
            LLMClient llmClient = createLLMClient();
            
            MultiToolOrchestrator orchestrator = new MultiToolOrchestrator(llmClient, mcpService);
            
            ChatInterface chat = new ChatInterface(mcpService, llmClient, orchestrator);
            chat.startChat();
            
        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static LLMClient createLLMClient() {
        String groqKey = System.getenv("GROQ_API_KEY");
        String geminiKey = System.getenv("GEMINI_API_KEY");
        
        if (groqKey != null && !groqKey.isEmpty()) {
            //return LLMClientFactory.createGroqClient(groqKey);
        } else if (geminiKey != null && !geminiKey.isEmpty()) {
           // return LLMClientFactory.createGeminiClient(geminiKey);
        } else {
            throw new RuntimeException("No LLM API key found. Set GROQ_API_KEY or GEMINI_API_KEY environment variable.");
        }
        
        return LLMClientFactory.createGroqClient(groqKey);
    }
}
