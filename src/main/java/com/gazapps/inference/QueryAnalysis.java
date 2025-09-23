package com.gazapps.inference;

import java.util.Map;
import java.util.Optional;

record QueryAnalysis(
    ExecutionType execution,
    String details, 
    Map<String, Object> parameters,
    Optional<MultiToolPlan> multiToolPlan  
) {
    enum ExecutionType { DIRECT_ANSWER, SINGLE_TOOL, MULTI_TOOL }
    
     public QueryAnalysis(ExecutionType execution, String details, Map<String, Object> parameters) {
        this(execution, details, parameters, Optional.empty());
    }
    
    public QueryAnalysis(ExecutionType execution, String details, Map<String, Object> parameters, MultiToolPlan multiToolPlan) {
        this(execution, details, parameters, Optional.ofNullable(multiToolPlan));
    }
    
     public boolean isMultiTool() {
        return execution == ExecutionType.MULTI_TOOL && multiToolPlan.isPresent();
    }
    
    public Optional<MultiToolPlan> getMultiToolPlan() {
        return multiToolPlan;
    }
}
