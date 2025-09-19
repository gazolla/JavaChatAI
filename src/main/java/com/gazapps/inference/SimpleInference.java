package com.gazapps.inference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazapps.llm.LLMClient;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.Tool;
import com.gazapps.mcp.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleInference {
    private final MCPService mcpService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private String lastResult = "";

    public SimpleInference(MCPService mcpService, LLMClient llmClient) {
        this.mcpService = mcpService;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public String processQuery(String query) {
        try {
            QueryAnalysis analysis = analyzeQuery(query);

            String result = switch (analysis.execution()) {
                case DIRECT_ANSWER -> generateDirectResponse(query);
                case SINGLE_TOOL -> executeSingleTool(analysis, query);
                case MULTI_TOOL -> "Multi-tool queries coming in future posts!";
            };

            if (analysis.execution() == QueryAnalysis.ExecutionType.SINGLE_TOOL) {
                lastResult = result;
            }

            return result;

        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private QueryAnalysis analyzeQuery(String query) throws Exception {
        List<Tool> availableTools = mcpService.getAllAvailableTools();
        String toolList = formatToolsForPrompt(availableTools);
        String prompt = PromptTemplates.getAnalysisPrompt(query, toolList, lastResult);

        String response = llmClient.send(prompt);
        return parseAnalysis(response);
    }

    private String formatToolsForPrompt(List<Tool> availableTools) throws JsonProcessingException {
        StringBuilder toolList = new StringBuilder();
        for (Tool tool : availableTools) {
            toolList.append("- ").append(tool.serverId()).append(":")
                    .append(tool.name()).append(" - ")
                    .append(tool.description())
                    .append(" (Parameters: ")
                    .append(formatParameters(tool.inputSchema()))
                    .append(")\n");
        }
        return toolList.toString();
    }

    private String formatParameters(Map<String, Object> inputSchema) throws JsonProcessingException {
        Map<String, Object> properties = (Map<String, Object>) inputSchema.getOrDefault("properties", Collections.emptyMap());
        List<String> required = (List<String>) inputSchema.getOrDefault("required", Collections.emptyList());

        if (properties.isEmpty()) {
            return "None";
        }

        List<String> paramDescriptions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();
            String paramType = (String) paramSchema.getOrDefault("type", "unknown");
            String paramDesc = (String) paramSchema.getOrDefault("description", "");
            Object defaultValue = paramSchema.getOrDefault("default", null);
            boolean isRequired = required.contains(paramName);

            StringBuilder desc = new StringBuilder(paramName + " (" + paramType);
            if (isRequired) {
                desc.append(", required");
            } else if (defaultValue != null) {
                String defaultValueStr = objectMapper.writeValueAsString(defaultValue);
                desc.append(", default: ").append(defaultValueStr.replace("\"", ""));
            }
            if (!paramDesc.isEmpty()) {
                desc.append(", ").append(paramDesc);
            }
            desc.append(")");
            paramDescriptions.add(desc.toString());
        }

        return String.join(", ", paramDescriptions);
    }

    private QueryAnalysis parseAnalysis(String response) {
        String cleaned = response.trim();
        String[] lines = cleaned.split("\n");

        if (cleaned.startsWith("DIRECT_ANSWER:")) {
            String reasoning = cleaned.substring("DIRECT_ANSWER:".length()).trim();
            System.out.println("Detected response modality: DIRECT_ANSWER");
            return new QueryAnalysis(QueryAnalysis.ExecutionType.DIRECT_ANSWER, reasoning, null);
        }

        if (cleaned.startsWith("SINGLE_TOOL:")) {
            String toolLine = lines[0].substring("SINGLE_TOOL:".length()).trim();
            String[] toolParts = toolLine.split(":");
            if (toolParts.length != 2) {
                System.out.println("Invalid tool format. Falling back to DIRECT_ANSWER.");
                return new QueryAnalysis(QueryAnalysis.ExecutionType.DIRECT_ANSWER, "Invalid tool format: " + toolLine, null);
            }
            String serverId = toolParts[0];
            String toolName = toolParts[1];
            Map<String, Object> parameters = new HashMap<>();
            
            for (String line : lines) {
                if (line.startsWith("PARAMS:")) {
                    String paramLine = line.substring("PARAMS:".length()).trim();
                    if (!paramLine.equals("{}")) {
                        try {
                            parameters = objectMapper.readValue(paramLine, Map.class);
                        } catch (Exception e) {
                            System.out.println("Failed to parse parameters: " + e.getMessage());
                        }
                    }
                    break;
                }
            }
            System.out.println("Detected response modality: SINGLE_TOOL");
            return new QueryAnalysis(QueryAnalysis.ExecutionType.SINGLE_TOOL, serverId + ":" + toolName, parameters);
        }

        if (cleaned.startsWith("MULTI_TOOL:")) {
            System.out.println("Detected response modality: MULTI_TOOL");
            return new QueryAnalysis(QueryAnalysis.ExecutionType.MULTI_TOOL, "Multiple tools needed", null);
        }
        
        System.out.println("Detected response modality: DIRECT_ANSWER (fallback)");
        return new QueryAnalysis(QueryAnalysis.ExecutionType.DIRECT_ANSWER, "Could not parse", null);
    }
    
    private String generateDirectResponse(String query) throws Exception {
        String prompt = PromptTemplates.getDirectAnswerPrompt(query);
        return llmClient.send(prompt);
    }
    
    private String executeSingleTool(QueryAnalysis analysis, String originalQuery) throws Exception {
        String[] toolParts = analysis.details().split(":");
        if (toolParts.length != 2) {
            System.out.println("Invalid tool format in QueryAnalysis: " + analysis.details());
            return generateDirectResponse(originalQuery);
        }
        String serverId = toolParts[0];
        String toolName = toolParts[1];
        Map<String, Object> parameters = analysis.parameters() != null ? analysis.parameters() : new HashMap<>();

        ToolResult result = mcpService.callTool(serverId, toolName, parameters);

        if (result.success()) {
            return generateToolResponse(originalQuery, toolName, result.content());
        } else {
            String fallbackPrompt = PromptTemplates.getFallbackPrompt(originalQuery);
            return llmClient.send(fallbackPrompt);
        }
    }
    
    private String generateToolResponse(String query, String toolName, String toolResult) throws Exception {
        String prompt = PromptTemplates.getToolResponsePrompt(query, toolName, toolResult);
        return llmClient.send(prompt);
    }

    private static final class PromptTemplates {
        private static final String ANALYSIS_PROMPT_TEMPLATE = """
	        Perform syntactic and semantic analysis on the following query to classify it, select 
	        the appropriate tool (if needed), and extract all required parameters:
	
	        Query: "%s"
	        
	        Available tools:
	        %s
      
        	Previous result: %s
      
	        Instructions:
	        1. Analyze the query's syntactic structure (e.g., identify key verbs, nouns, entities, and sentence patterns).
	        2. Determine the semantic intent (e.g., what the user wants to achieve, such as getting the time, day, weather, or performing a file operation).
	        3. Classify the query into one of:
	           - DIRECT_ANSWER: if the query can be answered using general knowledge.
	           - SINGLE_TOOL: if one tool is sufficient, specify the tool and extract its parameters.
	           - MULTI_TOOL: if multiple tools are required.
	        4. For SINGLE_TOOL:
	           - Select the most appropriate tool based on the intent and tool description.
	           - Extract ALL required parameters (e.g., location, date, path, filename).
	           - Handle special cases:
	             - If the query refers to "that" or "it", use the previous result as context.
	             - If a parameter is missing and cannot be inferred, use reasonable defaults.
	        5. Respond in this EXACT format:
	           For DIRECT_ANSWER:
	           DIRECT_ANSWER: [reason]
	           For SINGLE_TOOL:
	           SINGLE_TOOL: [server_id]:[tool_name]
	           REASONING: [why you chose this tool]
	           PARAMS: {"param1": "value1", "param2": "value2"}
	           For MULTI_TOOL:
	           MULTI_TOOL: multiple tools needed
	        6. Don't explain anything.   

            """;

        private static final String DIRECT_ANSWER_PROMPT_TEMPLATE = """
            Answer this question using your knowledge:
            
            %s
            
            Provide a helpful and accurate response.
            """;
            
        private static final String TOOL_RESPONSE_PROMPT_TEMPLATE = """
            Create a natural, helpful response for the user:
            
            User asked: "%s"
            Tool used: %s
            Tool result: %s
            
            Guidelines:
            1. Be conversational and natural
            2. Don't mention the tool name unless relevant
            
            Response:
            """;
            
        private static final String FALLBACK_PROMPT_TEMPLATE = """
            The tool I wanted to use isn't available right now.
            Can you help answer this question directly: %s
            """;

        public static String getAnalysisPrompt(String query, String toolList, String lastResult) {
            return ANALYSIS_PROMPT_TEMPLATE.formatted(query, toolList, lastResult);
        }

        public static String getDirectAnswerPrompt(String query) {
            return DIRECT_ANSWER_PROMPT_TEMPLATE.formatted(query);
        }

        public static String getToolResponsePrompt(String query, String toolName, String toolResult) {
            return TOOL_RESPONSE_PROMPT_TEMPLATE.formatted(query, toolName, toolResult);
        }

        public static String getFallbackPrompt(String query) {
            return FALLBACK_PROMPT_TEMPLATE.formatted(query);
        }
    }
}