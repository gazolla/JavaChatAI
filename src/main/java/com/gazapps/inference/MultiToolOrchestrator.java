package com.gazapps.inference;

import com.gazapps.llm.LLMClient;
import com.gazapps.mcp.MCPService;
import com.gazapps.mcp.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MultiToolOrchestrator {
    private static final Logger logger = LoggerFactory.getLogger(MultiToolOrchestrator.class);
    private static final int MAX_ITERATIONS = 10;
    
    private final LLMClient llmClient;
    private final MCPService mcpService;
    private ExecutorService executorService; // Lazy-initialized
    
    private final Map<String, ToolResult> stepResults = new HashMap<>();
    
    public MultiToolOrchestrator(LLMClient llmClient, MCPService mcpService) {
        this.llmClient = llmClient;
        this.mcpService = mcpService;
    }
    
     public ToolResult executePlan(MultiToolPlan plan) {
        if (!plan.isValid()) {
            return ToolResult.error("Invalid plan: " + plan);
        }
        
        if (plan.hasCircularDependencies()) {
            return ToolResult.error("Plan has circular dependencies");
        }
        
        logger.info("Executing {} plan with {} steps", plan.getPlanType(), plan.getSteps().size());
        stepResults.clear();
        
        try {
            return switch (plan.getPlanType()) {
                case SEQUENTIAL -> executeSequential(plan);
                case PARALLEL -> executeParallel(plan);
                case CONDITIONAL -> executeConditional(plan);
                case CHAINED -> executeChained(plan);
                case COMPETITIVE -> executeCompetitive(plan);
                case ITERATIVE -> executeIterative(plan);
            };
        } catch (Exception e) {
            logger.error("Error executing plan: {}", e.getMessage(), e);
            return ToolResult.error("Execution failed: " + e.getMessage(), e);
        }
    }
    
     private ToolResult executeSequential(MultiToolPlan plan) throws Exception {
        List<Step> steps = plan.getSteps();
        
        for (Step step : steps) {
            logger.debug("Executing step: {}", step.id());
            
            if (!areDependenciesSatisfied(step)) {
                return ToolResult.error("Dependencies not satisfied for step: " + step.id());
            }
            
            Map<String, Object> resolvedParams = resolveParameters(step);
            
            ToolResult result = mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
            
            stepResults.put(step.id(), result);
            
            if (!result.success()) {
                logger.warn("Step {} failed: {}", step.id(), result.message());
                return result; // Fail fast
            }
        }
        
        return aggregateResults(new ArrayList<>(stepResults.values()), plan.getAggregationPrompt());
    }
    
    private ToolResult executeParallel(MultiToolPlan plan) throws Exception {
        List<Step> independentSteps = plan.getIndependentSteps();
        List<Step> dependentSteps = plan.getSteps().stream()
            .filter(Step::hasDependencies)
            .toList();
        
        if (!independentSteps.isEmpty()) {
            ExecutorService executor = getExecutorService();
            
            List<CompletableFuture<Void>> futures = independentSteps.stream()
                .map(step -> CompletableFuture.runAsync(() -> {
                    try {
                        Map<String, Object> resolvedParams = resolveParameters(step);
                        ToolResult result = mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
                        synchronized (stepResults) {
                            stepResults.put(step.id(), result);
                        }
                    } catch (Exception e) {
                        logger.error("Error in parallel step {}: {}", step.id(), e.getMessage());
                        synchronized (stepResults) {
                            stepResults.put(step.id(), ToolResult.error("Parallel execution failed: " + e.getMessage()));
                        }
                    }
                }, executor))
                .toList();
            
             CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        for (Step step : dependentSteps) {
            if (!areDependenciesSatisfied(step)) {
                return ToolResult.error("Dependencies not satisfied for step: " + step.id());
            }
            
            Map<String, Object> resolvedParams = resolveParameters(step);
            ToolResult result = mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
            stepResults.put(step.id(), result);
            
            if (!result.success()) {
                return result;
            }
        }
        
        return aggregateResults(new ArrayList<>(stepResults.values()), plan.getAggregationPrompt());
    }
    
    private ToolResult executeConditional(MultiToolPlan plan) throws Exception {
        logger.warn("Conditional execution not fully implemented, falling back to sequential");
        return executeSequential(plan);
    }
    
    private ToolResult executeChained(MultiToolPlan plan) throws Exception {
        List<Step> steps = plan.getSteps();
        
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            logger.debug("Executing chained step {}: {}", i + 1, step.id());
            
            Map<String, Object> resolvedParams = resolveParameters(step);
            
            if (i > 0 && !stepResults.isEmpty()) {
                ToolResult previousResult = stepResults.get(steps.get(i - 1).id());
                if (previousResult != null && previousResult.success()) {
                    resolvedParams.putIfAbsent("input", previousResult.content());
                }
            }
            
            ToolResult result = mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
            stepResults.put(step.id(), result);
            
            if (!result.success()) {
                return result;
            }
        }
        
        return aggregateResults(new ArrayList<>(stepResults.values()), plan.getAggregationPrompt());
    }
    
     private ToolResult executeCompetitive(MultiToolPlan plan) throws Exception {
        ExecutorService executor = getExecutorService();
        
        List<CompletableFuture<ToolResult>> futures = plan.getSteps().stream()
            .map(step -> CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, Object> resolvedParams = resolveParameters(step);
                    return mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
                } catch (Exception e) {
                    return ToolResult.error("Competitive execution failed: " + e.getMessage());
                }
            }, executor))
            .toList();
        
        List<ToolResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        for (ToolResult result : results) {
            if (result.success()) {
                return result;
            }
        }
        
        return ToolResult.error("All competitive executions failed");
    }
    
     private ToolResult executeIterative(MultiToolPlan plan) throws Exception {
        int iteration = 0;
        ToolResult lastResult = null;
        
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            logger.debug("Iterative execution - iteration {}", iteration);
            
            for (Step step : plan.getSteps()) {
                Map<String, Object> resolvedParams = resolveParameters(step);
                lastResult = mcpService.callTool(step.serverId(), step.toolName(), resolvedParams);
                stepResults.put(step.id() + "_iter" + iteration, lastResult);
                
                if (!lastResult.success()) {
                    return lastResult;
                }
            }
            
            if (plan.getConditionPrompt() != null && lastResult != null) {
                String conditionCheck = buildConditionPrompt(plan.getConditionPrompt(), lastResult);
                String response = llmClient.send(conditionCheck);
                
                if (response.toLowerCase().contains("stop") || response.toLowerCase().contains("complete")) {
                    break;
                }
            }
        }
        
        return lastResult != null ? lastResult : ToolResult.error("Iterative execution produced no results");
    }
    
    private boolean areDependenciesSatisfied(Step step) {
        return step.dependencies().stream()
            .allMatch(depId -> stepResults.containsKey(depId) && stepResults.get(depId).success());
    }
    
     private Map<String, Object> resolveParameters(Step step) {
        Map<String, Object> resolved = new HashMap<>(step.parameters());
        
        for (Map.Entry<String, Object> entry : resolved.entrySet()) {
            if (entry.getValue() instanceof String paramValue) {
                String resolvedValue = resolveVariables(paramValue);
                
                // Sanitize file paths to be relative
                if ("path".equals(entry.getKey()) && resolvedValue.startsWith("/")) {
                    resolvedValue = resolvedValue.substring(1);
                    logger.debug("Sanitized path from '{}' to '{}'", paramValue, resolvedValue);
                }
                
                resolved.put(entry.getKey(), resolvedValue);
            }
        }
        
        return resolved;
    }
    
    private String resolveVariables(String value) {
        Pattern pattern = Pattern.compile("\\$\\{([^.]+)\\.([^}]+)\\}");
        Matcher matcher = pattern.matcher(value);
        
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String stepId = matcher.group(1);
            String field = matcher.group(2);
            
            ToolResult stepResult = stepResults.get(stepId);
            if (stepResult != null && stepResult.success()) {
                String replacement = extractField(stepResult, field);
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
            } else {
                // Keep variable if not resolved
                matcher.appendReplacement(resolved, matcher.group(0));
            }
        }
        matcher.appendTail(resolved);
        
        return resolved.toString();
    }
    
    private String extractField(ToolResult result, String field) {
        return switch (field) {
            case "result" -> result.content();
            case "success" -> String.valueOf(result.success());
            case "message" -> result.message();
            default -> result.content(); // Default to result content
        };
    }
    
    private ToolResult aggregateResults(List<ToolResult> results, String aggregationPrompt) throws Exception {
        if (results.isEmpty()) {
            return ToolResult.error("No results to aggregate");
        }
        
        if (results.size() == 1) {
            return results.get(0);
        }
        
        if (aggregationPrompt != null && !aggregationPrompt.trim().isEmpty()) {
            String prompt = buildAggregationPrompt(aggregationPrompt, results);
            String aggregatedContent = llmClient.send(prompt);
            return ToolResult.success(null, aggregatedContent);
        }
        
        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            ToolResult result = results.get(i);
            if (result.success()) {
                combined.append("Result ").append(i + 1).append(": ").append(result.content());
                if (i < results.size() - 1) {
                    combined.append("\n\n");
                }
            }
        }
        
        return ToolResult.success(null, combined.toString());
    }
    
    private String buildAggregationPrompt(String aggregationPrompt, List<ToolResult> results) {
        StringBuilder prompt = new StringBuilder(aggregationPrompt);
        prompt.append("\n\nResults to aggregate:\n");
        
        for (int i = 0; i < results.size(); i++) {
            ToolResult result = results.get(i);
            if (result.success()) {
                prompt.append("Result ").append(i + 1).append(": ").append(result.content()).append("\n");
            }
        }
        
        return prompt.toString();
    }
    
    private String buildConditionPrompt(String conditionPrompt, ToolResult lastResult) {
        return conditionPrompt + "\n\nLast result: " + lastResult.content() + 
               "\n\nShould we continue? Answer 'continue' or 'stop'.";
    }
    
    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool();
        }
        return executorService;
    }
    
    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
