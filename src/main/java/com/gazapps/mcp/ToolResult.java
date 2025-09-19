package com.gazapps.mcp;

public record ToolResult(
    boolean success,
    Tool tool,
    String content,
    String message,
    Exception error
) {
    public static ToolResult success(Tool tool, String content) {
        return new ToolResult(true, tool, content, "Success", null);
    }
    
    public static ToolResult error(String message) {
        return new ToolResult(false, null, null, message, null);
    }
    
    public static ToolResult error(String message, Exception error) {
        return new ToolResult(false, null, null, message, error);
    }
}
