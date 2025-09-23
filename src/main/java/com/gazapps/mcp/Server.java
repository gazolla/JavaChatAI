package com.gazapps.mcp;

import java.util.*;

public class Server {
    private final String id;
    private final String name;
    private boolean connected;
    private final List<Tool> tools;
    
    public Server(String id, String name, boolean connected) {
        this.id = id;
        this.name = name;
        this.connected = connected;
        this.tools = new ArrayList<>();
    }
    
    public void addTool(Tool tool) {
        tools.add(tool);
    }
    
    public Tool getTool(String toolName) {
        return tools.stream()
            .filter(tool -> tool.name().equals(toolName))
            .findFirst()
            .orElse(null);
    }
    
    public int getToolCount() {
        return tools.size();
    }
    
    public String id() { return id; }
    public String name() { return name; }
    public boolean isConnected() { return connected; }
    public List<Tool> getTools() { return List.copyOf(tools); }
}
