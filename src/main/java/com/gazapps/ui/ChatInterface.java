package com.gazapps.ui;

import java.util.Scanner;

import com.gazapps.inference.SimpleInference;
import com.gazapps.llm.LLMClient;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.Server;
import com.gazapps.mcp.Tool;

import java.util.List;
import java.util.Map;

public class ChatInterface {
    private final SimpleInference inference; // From Posts 5-6
    private final MCPService mcpService;     // From Post 3
    private final Scanner scanner;
    private boolean running = true;
    
    public ChatInterface(MCPService mcpService, LLMClient llmClient) {
        this.mcpService = mcpService;
        this.inference = new SimpleInference(mcpService, llmClient);
        this.scanner = new Scanner(System.in);
    }
    
    public void startChat() {
        showWelcome();
        
        while (running) {
            String input = getUserInput();
            
            if (isExitCommand(input)) {
                running = false;
                System.out.println("👋 Goodbye!");
                continue;
            }
            
            processUserQuery(input);
        }
        
        cleanup();
    }
    
    private void showWelcome() {
        List<Tool> tools = mcpService.getAllAvailableTools();
        Map<String, Server> servers = mcpService.getConnectedServers();
        
        System.out.println("🤖 AI Assistant Ready!");
        
        if (servers.isEmpty()) {
            System.out.println("⚠️ No external tools available - I can still answer questions directly.");
        } else {
            System.out.printf("✅ Connected to %d servers with %d tools.%n", servers.size(), tools.size());
        }
        
        // Quick LLM health check
        try {
            inference.processQuery("Hi"); // Simple test
            System.out.println("✅ AI service is working.");
        } catch (Exception e) {
            System.out.println("⚠️ AI service may be slow - responses might be delayed.");
        }
        
        System.out.println("Type 'exit' to quit.\n");
    }
    
    private String getUserInput() {
        System.out.print("You: ");
        String input = scanner.nextLine().trim();
        
        // Handle empty input
        if (input.isEmpty()) {
            return "";
        }
        
        // Basic input validation - keep messages reasonable length
        if (input.length() > 500) {
            System.out.println("🤖 Please keep your message shorter than 500 characters.\n");
            return "";
        }
        
        // Remove control characters that might cause issues
        input = input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        
        return input;
    }
    
    private boolean isExitCommand(String input) {
        String lower = input.toLowerCase();
        return lower.equals("exit") || lower.equals("quit") || lower.equals("bye");
    }
    
    private void processUserQuery(String input) {
        if (input.isEmpty()) {
            return;
        }
        
        try {
            String response = inference.processQuery(input);
            System.out.println("🤖 " + response + "\n");
            
        } catch (Exception e) {
            String error = e.getMessage().toLowerCase();
            
            if (error.contains("timeout") || error.contains("timed out")) {
                System.out.println("🤖 That took too long to process. Please try something simpler.\n");
            } else if (error.contains("rate limit") || error.contains("limit")) {
                System.out.println("🤖 I'm getting too many requests. Please wait a moment.\n");
            } else if (error.contains("unavailable") || error.contains("connection")) {
                System.out.println("🤖 I'm having connection issues. Please try again.\n");
            } else {
                System.out.println("🤖 I'm having trouble with that. Please try rephrasing your question.\n");
            }
        }
    }
    
    private void cleanup() {
        scanner.close();
        mcpService.close();
    }
}
