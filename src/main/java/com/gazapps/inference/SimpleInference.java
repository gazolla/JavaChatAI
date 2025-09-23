package com.gazapps.inference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazapps.llm.LLMClient;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.Tool;
import com.gazapps.mcp.ToolResult;

public class SimpleInference {
    private final MCPService mcpService;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;
    private String lastResult = "";
    private MultiToolOrchestrator orchestrator; // Injected dependency

    public SimpleInference(MCPService mcpService, LLMClient llmClient) {
        this.mcpService = mcpService;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    public String processQuery(String query) {
        try {
            QueryAnalysis analysis = analyzeQuery(query);
            //System.out.println(analysis);

            String result = switch (analysis.execution()) {
                case DIRECT_ANSWER -> generateDirectResponse(query);
                case SINGLE_TOOL -> executeSingleTool(analysis, query);
                case MULTI_TOOL -> executeMultiTool(analysis, query);
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
            String planName = "";
            String reasoning = "";
            String toolsInfo = "";
            
            for (String line : lines) {
                if (line.startsWith("MULTI_TOOL:")) {
                    planName = line.substring("MULTI_TOOL:".length()).trim();
                } else if (line.startsWith("REASONING:")) {
                    reasoning = line.substring("REASONING:".length()).trim();
                } else if (line.startsWith("TOOLS:")) {
                    toolsInfo = line.substring("TOOLS:".length()).trim();
                }
            }
            
            Map<String, Object> multiToolInfo = new HashMap<>();
            multiToolInfo.put("planName", planName);
            multiToolInfo.put("reasoning", reasoning);
            multiToolInfo.put("toolsInfo", toolsInfo);
            
            System.out.println("Detected response modality: MULTI_TOOL");
            
            return new QueryAnalysis(
                QueryAnalysis.ExecutionType.MULTI_TOOL, 
                planName, 
                multiToolInfo, 
                Optional.empty() 
            );
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
        		1. SYNTACTIC ANALYSIS:
        		   - Extract ALL action verbs from the query (e.g., create, move, search, save, write, get)
        		   - Identify the target objects for each action (e.g., file, folder, data, information)
        		   - Note any connectors or sequencing words (and, then, also, after)

        		2. SEMANTIC ANALYSIS:
        		   - For each action verb identified, determine which available tool could handle it
        		   - Consider if one single tool can perform ALL the actions mentioned in the query
        		   - Evaluate if actions need external data or have dependencies between them

        		3. TOOL MAPPING & CLASSIFICATION:
        		   - Map each action to the most appropriate available tool based on tool descriptions
        		   - Count how many DIFFERENT tools are required to complete the entire request
        		   - Apply classification logic:
	        		    * DIRECT_ANSWER: No external tools needed (informational/knowledge queries)
 
			       		* SINGLE_TOOL:
			        		   - Select the most appropriate tool based on the intent and tool description
			        		   - Extract ALL required parameters (e.g., location, date, path, filename)
			        		   - Handle special cases:
			        		     - If the query refers to "that" or "it", use the previous result as context
			        		     - If a parameter is missing and cannot be inferred, use reasonable defaults

						* MULTI_TOOL STRATEGY SELECTION:
							   If MULTI_TOOL is required, select the appropriate execution strategy based on the query’s syntactic structure and semantic intent. Use the following strategies with their syntactic patterns and semantic examples to guide classification:
							
							   - SEQUENTIAL: Execute tools one after another in order (most common).
							     Use when: Actions must be done in a specific order, or one depends on the output of another.
							     Syntactic Pattern: "[action1 verb] X and [action2 verb] Y" or "[action1 verb] X, then [action2 verb] Y".
							     Semantic Intent: One action’s completion is a prerequisite for the next.
							     Example:
							       - Query: "create a folder weather in documents and move nyc.txt to it"
							         - Syntactic: Verbs: "create," "move"; Objects: "folder weather," "nyc.txt"; Connector: "and".
							         - Semantic: Create a directory (`create_directory`), then move a file into it (`move_file`).
							         - Tools: `filesystem-server:create_directory` (creates directory), `filesystem-server:move_file` (moves file).
							         - Dependency: Directory must exist before file can be moved.
							
							   - PARALLEL: Execute multiple tools simultaneously.
							     Use when: Actions are independent and can run concurrently without dependencies.
							     Syntactic Pattern: "[action verb] X in [location1], [location2], and [location3]" or "get X, Y, and Z".
							     Semantic Intent: Perform the same or similar actions across multiple sources or entities simultaneously.
							     Example:
							       - Query: "check the weather in Tokyo, Osaka, and Kyoto"
							         - Syntactic: Verb: "check"; Objects: "weather in Tokyo," "weather in Osaka," "weather in Kyoto"; Connector: "and".
							         - Semantic: Retrieve weather data for multiple locations concurrently (`weather_query` for each location).
							         - Tools: `weather-server:weather_query` (called three times for Tokyo, Osaka, Kyoto).
							         - Dependency: None; actions are independent.
							
							   - CHAINED: Output of one tool becomes input of the next automatically.
							     Use when: A clear data flow pipeline exists (e.g., retrieve data, transform it, save it).
							     Syntactic Pattern: "[action1 verb] X, [action2 verb] to Y, [action3 verb] to Z".
							     Semantic Intent: Each action transforms or processes the output of the previous action.
							     Example:
							       - Query: "extract text from a PDF, translate it to Spanish, and save it to a file"
							         - Syntactic: Verbs: "extract," "translate," "save"; Objects: "text," "Spanish," "file"; Connectors: "and," implied sequence.
							         - Semantic: Extract text (`pdf_reader`), translate text (`translator`), save result (`write_file`).
							         - Tools: `document-server:pdf_reader`, `language-server:translator`, `filesystem-server:write_file`.
							         - Dependency: Extracted text feeds into translator, translated text feeds into file write.
							
							   - COMPETITIVE: Multiple tools run in parallel, best result selected.
							     Use when: Multiple tools can perform the same action, and the best result is chosen based on criteria (e.g., cost, quality).
							     Syntactic Pattern: "check/get X on/from [source1], [source2], and [source3] and recommend/choose best".
							     Semantic Intent: Compare results from multiple sources to select the optimal one.
							     Example:
							       - Query: "check the price of a laptop on Amazon, eBay, and Walmart and recommend the cheapest"
							         - Syntactic: Verb: "check"; Object: "price of a laptop"; Sources: "Amazon," "eBay," "Walmart"; Connector: "and"; Action: "recommend".
							         - Semantic: Query price from multiple sources (`price_checker`), then compare results to select the cheapest.
							         - Tools: `commerce-server:price_checker` (called for Amazon, eBay, Walmart), `analysis-server:compare` (selects cheapest).
							         - Dependency: Prices must be retrieved before comparison.
							
							   - ITERATIVE: Repeat tools until a condition is met.
							     Use when: A process requires refinement or repetition until a goal is achieved.
							     Syntactic Pattern: "do X, check Y, repeat until Z" or "keep [action verb] X until Y".
							     Semantic Intent: Perform an action, evaluate the result, and repeat if the condition is not met.
							     Example:
							       - Query: "write a draft, check its grammar, and adjust until it’s perfect"
							         - Syntactic: Verbs: "write," "check," "adjust"; Objects: "draft," "grammar"; Condition: "until it’s perfect".
							         - Semantic: Generate text (`text_generator`), check grammar (`grammar_checker`), revise text if errors exist (`text_generator` again).
							         - Tools: `writing-server:text_generator`, `language-server:grammar_checker`.
							         - Dependency: Grammar check result determines whether to repeat text generation.
							
							   - Extract ALL required parameters for each tool (e.g., path, source_path, destination_path, location).
							   - Ensure parameters account for dependencies (e.g., output of one tool as input for the next).
			        		
        		CRITICAL RULE: A single tool call cannot perform multiple distinct actions or capabilities.
        		If the query requires different types of operations (create + move, search + save, get + write), 
        		it needs multiple tools regardless of how the request is phrased.

 
        		6. Respond in this EXACT format:
        		   For DIRECT_ANSWER:
        		   DIRECT_ANSWER: [reason]
        		   For SINGLE_TOOL:
        		   SINGLE_TOOL: [server_id]:[tool_name]
        		   REASONING: [why you chose this tool]
        		   PARAMS: {"param1": "value1", "param2": "value2"}
        		   For MULTI_TOOL:
        		   MULTI_TOOL: [strategy name: SEQUENTIAL/PARALLEL/CHAINED/COMPETITIVE/ITERATIVE]
        		   REASONING: [Why this strategy and which tools are needed for each action]
        		   TOOLS: [tool1]: [action1 description], [tool2]: [action2 description]

        		7. Don't explain anything.
        		""";

        private static final String DIRECT_ANSWER_PROMPT_TEMPLATE = """
            Create a short, natural response for the user using your knowledge:
            
            %s
            
            """;
            
        private static final String TOOL_RESPONSE_PROMPT_TEMPLATE = """
            Create a short, natural, helpful response for the user:
           
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
        
        // NEW: Multi-tool planning prompt
        private static final String MULTI_TOOL_PLANNING_TEMPLATE = """
            Create a detailed execution plan for this multi-tool query:
            "%s"
            
            Available tools:
            %s
            
            The initial analysis suggested planType: %s. 
			Use this plan type unless there's a strong technical reason not to.
			If you choose a different plan type, briefly explain why in a comment.            
            
            Analyze the query and return a JSON plan with:
            - planType: "SEQUENTIAL", "PARALLEL", "CHAINED", "CONDITIONAL", "COMPETITIVE", or "ITERATIVE"
            - steps: array of objects with {"id": "unique_id", "serverId": "server_id", "toolName": "tool_name", "parameters": {}, "dependencies": []}
            - conditionPrompt: (optional) for conditional/iterative logic
            - aggregationPrompt: (optional) for result consolidation
            
            Guidelines:
            - Use SEQUENTIAL for dependent operations
            - Use PARALLEL for independent operations
            - Use CHAINED when output of one becomes input of next
            - Include dependencies array with step IDs this step depends on
            - Use variable substitution like ${previous_step_id.result} in parameters
            - For file paths, use RELATIVE paths only (e.g., "documents/file.txt" NOT "/documents/file.txt")
            - All paths must be relative to the current working directory
            - Do not use leading slashes (/) in file paths
            
			 CRITICAL CONSTRAINTS:
			- ONLY use tools from the "Available tools" list above - do NOT invent tools
			- Use your complete knowledge base to provide any required parameters (coordinates, timezones, conversions, etc.)
			- Match tool names and parameter names exactly as specified in tool descriptions
			- If multiple independent operations of same type, use PARALLEL planType for efficiency

            Return ONLY the JSON plan:
            """;
            
        public static String getMultiToolPlanningPrompt(String query, String toolList, String analysis) {
            return MULTI_TOOL_PLANNING_TEMPLATE.formatted(query, toolList, analysis);
        }
    }
    
    /**
     * Set the multi-tool orchestrator (dependency injection).
     */
    public void setOrchestrator(MultiToolOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
    
    /**
     * Execute multi-tool query using specialized analysis and orchestration.
     */
    private String executeMultiTool(QueryAnalysis analysis, String originalQuery) throws Exception {
        if (orchestrator == null) {
            return "Multi-tool execution not available - orchestrator not configured";
        }
        
        MultiToolPlan plan;
        
        // Check if plan is already in the analysis
        if (analysis.isMultiTool() && analysis.getMultiToolPlan().isPresent()) {
            plan = analysis.getMultiToolPlan().get();
        } else {
            // Analyze query with specialized multi-tool prompt
            plan = analyzeMultiToolQuery(originalQuery, analysis.details());
        }
        
        if (!plan.isValid()) {
            return "Could not create valid execution plan for this query.";
        }
        
        // Execute the plan
        ToolResult result = orchestrator.executePlan(plan);
        
        if (result.success()) {
            return generateToolResponse(originalQuery, "multi-tool plan", result.content());
        } else {
            // Fallback to direct answer if multi-tool execution fails
            String fallbackPrompt = PromptTemplates.getFallbackPrompt(originalQuery);
            return llmClient.send(fallbackPrompt);
        }
    }
    
    /**
     * Analyze query with specialized multi-tool planning prompt.
     * @param analysis 
     */
    private MultiToolPlan analyzeMultiToolQuery(String query, String analysis) throws Exception {
        List<Tool> availableTools = mcpService.getAllAvailableTools();
        String toolList = formatToolsForPrompt(availableTools);
        String prompt = PromptTemplates.getMultiToolPlanningPrompt(query, toolList, analysis);
        
        String response = llmClient.send(prompt);
        
        try {
        	response = response.trim();
             if (response.startsWith("```json")) {
            	 response = response.substring(7);
             }
             if (response.endsWith("```")) {
            	 response = response.substring(0, response.length() - 3);
             }
             response = response.trim();
            return objectMapper.readValue(response, MultiToolPlan.class);
        } catch (Exception e) {
            System.err.println("Failed to parse multi-tool plan: " + e.getMessage());
            System.err.println("LLM Response: " + response);
            

            return new MultiToolPlan(PlanType.SEQUENTIAL, new ArrayList<>(), null, null);
        }
    }
}