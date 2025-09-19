package com.gazapps.mcp;

import java.util.Collections;
import java.util.Map;

public class Tool {
    private final String name;
    private final String description;
    private final String serverId;
    private final Map<String, Object> inputSchema; 

    public Tool(String name, String description, String serverId, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.serverId = serverId;
        this.inputSchema = inputSchema != null ? inputSchema : Collections.emptyMap();
    }

    public String name() { return name; }
    public String description() { return description; }
    public String serverId() { return serverId; }
    public Map<String, Object> inputSchema() { return inputSchema; }
}