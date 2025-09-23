package com.gazapps.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class MCPService {
    private final Map<String, Server> servers;
    private final Map<String, McpSyncClient> clients;
    
    public MCPService() {
        this.servers = new ConcurrentHashMap<>();
        this.clients = new ConcurrentHashMap<>();
        
        initializeServers();
    }
    
    private void initializeServers() {
        connectToWeatherServer();
        connectToFilesystemServer();
        connectToTimeServer();
    }
    
    private void connectToWeatherServer() {
        try {
            String[] command = {"npx", "-y", "@h1deya/mcp-server-weather"};
            McpSyncClient client = createClient("weather-server", command);
            
            if (client != null) {
                Server server = new Server("weather-server", "Weather", true);
                loadServerTools(server, client);
                
                servers.put("weather-server", server);
                clients.put("weather-server", client);
                
                System.out.println("✅ Weather server connected with " + server.getToolCount() + " tools");
            }
        } catch (Exception e) {
            System.out.println("❌ Weather server failed: " + e.getMessage());
        }
    }
    
    private void connectToFilesystemServer() {
        try {
        	String basePath = ".";
            String[] command = {"npx", "-y", "@modelcontextprotocol/server-filesystem", basePath};
            McpSyncClient client = createClient("filesystem-server", command);
            
            if (client != null) {
                Server server = new Server("filesystem-server", "Filesystem", true);
                loadServerTools(server, client);
                
                servers.put("filesystem-server", server);
                clients.put("filesystem-server", client);
                
                System.out.println("✅ Filesystem server connected with " + server.getToolCount() + " tools");
            }
        } catch (Exception e) {
            System.out.println("❌ Filesystem server failed: " + e.getMessage());
        }
    }
    
    private void connectToTimeServer() {
        try {
            String[] command = {"uvx", "mcp-server-time"};
            McpSyncClient client = createClient("time-server", command);
            
            if (client != null) {
                Server server = new Server("time-server", "Time", true);
                loadServerTools(server, client);
                
                servers.put("time-server", server);
                clients.put("time-server", client);
                
                System.out.println("✅ Time server connected with " + server.getToolCount() + " tools");
            }
        } catch (Exception e) {
            System.out.println("❌ Time server failed: " + e.getMessage());
        }
    }
    
    private McpSyncClient createClient(String serverId, String[] command) {
        try {
            // Handle Windows vs Unix commands
            String[] fullCommand;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                fullCommand = new String[command.length + 2];
                fullCommand[0] = "cmd.exe";
                fullCommand[1] = "/c";
                System.arraycopy(command, 0, fullCommand, 2, command.length);
            } else {
                fullCommand = command;
            }
            
            ServerParameters serverParams = ServerParameters.builder(fullCommand[0])
                .args(Arrays.copyOfRange(fullCommand, 1, fullCommand.length))
                .build();
            
            StdioClientTransport transport = new StdioClientTransport(serverParams);
            
            McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(15))
                .build();
           client.initialize();
            
            return client;
            
        } catch (Exception e) {
            System.out.println("Failed to create client for " + serverId + ": " + e.getMessage());
            return null;
        }
    }
    
    private void loadServerTools(Server server, McpSyncClient client) {
        try {
            ListToolsResult toolsResult = client.listTools();
            
            for (io.modelcontextprotocol.spec.McpSchema.Tool mcpTool : toolsResult.tools()) {
                Map<String, Object> inputSchema = convertMcpSchema(mcpTool.inputSchema());
                Tool tool = new Tool(mcpTool.name(), mcpTool.description(), server.id(), inputSchema);
                server.addTool(tool);
            }
            
        } catch (Exception e) {
            System.out.println("Error loading tools for " + server.id() + ": " + e.getMessage());
        }
    }
    
    private static Map<String, Object> convertMcpSchema(Object inputSchema) {
        if (inputSchema == null) {
            return Collections.emptyMap();
        }
        
        try {
            if (inputSchema instanceof io.modelcontextprotocol.spec.McpSchema.JsonSchema) {
                io.modelcontextprotocol.spec.McpSchema.JsonSchema jsonSchema = 
                    (io.modelcontextprotocol.spec.McpSchema.JsonSchema) inputSchema;
                
                Map<String, Object> result = new HashMap<>();
                
                if (jsonSchema.properties() != null && !jsonSchema.properties().isEmpty()) {
                    result.put("properties", new HashMap<>(jsonSchema.properties()));
                }
                
                if (jsonSchema.required() != null && !jsonSchema.required().isEmpty()) {
                    result.put("required", new ArrayList<>(jsonSchema.required()));
                }
                
                if (jsonSchema.type() != null) {
                    result.put("type", jsonSchema.type());
                }
                
                return result;
            }
            
             if (inputSchema instanceof Map) {
                return new HashMap<>((Map<String, Object>) inputSchema);
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao converter schema MCP: " + e.getMessage());
            e.printStackTrace();
        }
        
        return Collections.emptyMap();
    }
    
    @SuppressWarnings("unchecked")
    public ToolResult callTool(String serverId, String toolName, Map<String, Object> args) {

        Server server = servers.get(serverId);
        if (server == null) {
            return ToolResult.error("Server not found: " + serverId);
        }
        
        if (!server.isConnected()) {
            return ToolResult.error("Server is not connected");
        }
        
        Tool tool = server.getTool(toolName);
        if (tool == null) {
            return ToolResult.error("Tool not found: " + toolName);
        }

        Map<String, Object> schema = tool.inputSchema();
        List<String> requiredParams = (List<String>) schema.getOrDefault("required", Collections.emptyList());
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Collections.emptyMap());
        

        Map<String, Object> convertedArgs = new HashMap<>(args);
        

        for (String param : requiredParams) {
            if (!args.containsKey(param)) {
                return ToolResult.error("Missing required parameter: " + param);
            }
        }
        
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String paramName = entry.getKey();
            Object paramValue = entry.getValue();
            Map<String, Object> paramSchema = (Map<String, Object>) properties.get(paramName);
            if (paramSchema != null) {
                String expectedType = (String) paramSchema.get("type");
                if (expectedType != null) {
                    if (!isValidType(paramValue, expectedType)) {
                        return ToolResult.error("Invalid type for parameter " + paramName + ": expected " + expectedType);
                    }
                    if ("number".equals(expectedType) && paramValue instanceof String stringValue) {
                        try {
                            convertedArgs.put(paramName, Double.parseDouble(stringValue));
                        } catch (NumberFormatException e) {
                            return ToolResult.error("Cannot convert parameter " + paramName + " to number: " + stringValue);
                        }
                    }
                    else if ("boolean".equals(expectedType) && paramValue instanceof String stringValue) {
                        if ("true".equalsIgnoreCase(stringValue)) {
                            convertedArgs.put(paramName, true);
                        } else if ("false".equalsIgnoreCase(stringValue)) {
                            convertedArgs.put(paramName, false);
                        } else {
                            return ToolResult.error("Cannot convert parameter " + paramName + " to boolean: " + stringValue);
                        }
                    }
                }
            }
        }
        
        return executeToolWithRetry(server, tool, convertedArgs);
    }

    private boolean isValidType(Object value, String expectedType) {
        if (value == null) {
            return false; 
        }

        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "number" -> {
                if (value instanceof Number) {
                    yield true;
                }
                if (value instanceof String stringValue) {
                    try {
                        Double.parseDouble(stringValue);
                        yield true;
                    } catch (NumberFormatException e) {
                        yield false;
                    }
                }
                yield false;
            }
            case "boolean" -> {
                if (value instanceof Boolean) {
                    yield true;
                }
                if (value instanceof String stringValue) {
                    yield "true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue);
                }
                yield false;
            }
            default -> true; 
        };
    }
    
    private ToolResult executeToolWithRetry(Server server, Tool tool, Map<String, Object> args) {
        Exception lastException = null;
        int maxRetries = 2;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executeToolDirect(server, tool, args);
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempt); // Simple backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        return ToolResult.error("Failed after " + maxRetries + " attempts", lastException);
    }
    
    private ToolResult executeToolDirect(Server server, Tool tool, Map<String, Object> args) throws Exception {
        McpSyncClient client = clients.get(server.id());
        
        CallToolRequest request = new CallToolRequest(tool.name(), args != null ? args : Map.of());
        CallToolResult result = client.callTool(request);
        
        if (result.isError() != null && result.isError()) {
            throw new Exception("Tool execution failed: " + result.toString());
        }
        
        String content = extractContent(result.content());
        return ToolResult.success(tool, content);
    }
    
    private String extractContent(List<Content> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            return "No content returned";
        }
        
        for (Content content : contentList) {
            if (content instanceof TextContent textContent) {
                if (textContent.text() != null && !textContent.text().trim().isEmpty()) {
                    return textContent.text();
                }
            }
        }
        
        return "No text content found";
    }
    
    public List<Tool> getAllAvailableTools() {
        return servers.values().stream()
            .filter(Server::isConnected)
            .flatMap(server -> server.getTools().stream())
            .toList();
    }
    
    public boolean isServerConnected(String serverId) {
        Server server = servers.get(serverId);
        return server != null && server.isConnected();
    }
    
    public Map<String, Server> getConnectedServers() {
        return servers.entrySet().stream()
            .filter(entry -> entry.getValue().isConnected())
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    public void close() {
        for (String serverId : new ArrayList<>(servers.keySet())) {
            disconnectServer(serverId);
        }
        
        servers.clear();
        clients.clear();
    }
    
     public boolean validateToolCall(String serverId, String toolName, Map<String, Object> args) {
        Server server = servers.get(serverId);
        if (server == null || !server.isConnected()) {
            return false;
        }
        
        Tool tool = server.getTool(toolName);
        if (tool == null) {
            return false;
        }
        
        return validateParameters(tool, args);
    }
    
    @SuppressWarnings("unchecked")
    private boolean validateParameters(Tool tool, Map<String, Object> args) {
        Map<String, Object> schema = tool.inputSchema();
        List<String> requiredParams = (List<String>) schema.getOrDefault("required", Collections.emptyList());
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Collections.emptyMap());
        
        // Check required parameters
        for (String param : requiredParams) {
            if (!args.containsKey(param)) {
                return false;
            }
        }
        
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String paramName = entry.getKey();
            Object paramValue = entry.getValue();
            Map<String, Object> paramSchema = (Map<String, Object>) properties.get(paramName);
            
            if (paramSchema != null) {
                String expectedType = (String) paramSchema.get("type");
                if (expectedType != null && !isValidType(paramValue, expectedType)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private void disconnectServer(String serverId) {
        try {
            McpSyncClient client = clients.remove(serverId);
            if (client != null) {
                client.close();
            }
            
            Server server = servers.get(serverId);
            if (server != null) {
                System.out.println("Server " + serverId + " disconnected");
            }
            
        } catch (Exception e) {
            System.out.println("Error disconnecting server " + serverId + ": " + e.getMessage());
        }
    }
}
