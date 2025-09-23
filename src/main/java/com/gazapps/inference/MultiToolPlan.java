package com.gazapps.inference;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class MultiToolPlan {
    private final PlanType planType;
    private final List<Step> steps;
    private final String conditionPrompt;
    private final String aggregationPrompt;
    
    public MultiToolPlan() {
        this(PlanType.SEQUENTIAL, new ArrayList<>(), null, null);
    }
    
    @JsonCreator
    public MultiToolPlan(
            @JsonProperty("planType") PlanType planType,
            @JsonProperty("steps") List<Step> steps,
            @JsonProperty("conditionPrompt") String conditionPrompt,
            @JsonProperty("aggregationPrompt") String aggregationPrompt) {
        this.planType = planType != null ? planType : PlanType.SEQUENTIAL;
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.conditionPrompt = conditionPrompt;
        this.aggregationPrompt = aggregationPrompt;
    }
    
    public PlanType getPlanType() { return planType; }
    public List<Step> getSteps() { return new ArrayList<>(steps); }
    public String getConditionPrompt() { return conditionPrompt; }
    public String getAggregationPrompt() { return aggregationPrompt; }
    
   public boolean isValid() {
        if (planType == null || steps == null || steps.isEmpty()) {
            return false;
        }
        
         for (Step step : steps) {
            if (!step.isValid()) {
                return false;
            }
        }
        
        if ((planType == PlanType.CONDITIONAL || planType == PlanType.ITERATIVE) 
            && (conditionPrompt == null || conditionPrompt.trim().isEmpty())) {
            return false;
        }
        
        return true;
    }
    
      public boolean hasCircularDependencies() {
        for (Step step : steps) {
            if (hasCircularDependency(step.id(), step.dependencies(), new ArrayList<>())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasCircularDependency(String stepId, List<String> dependencies, List<String> visited) {
        if (visited.contains(stepId)) {
            return true;
        }
        
        visited.add(stepId);
        
        for (String depId : dependencies) {
            Step depStep = getStepById(depId);
            if (depStep != null) {
                if (hasCircularDependency(depStep.id(), depStep.dependencies(), new ArrayList<>(visited))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    public Step getStepById(String stepId) {
        return steps.stream()
            .filter(step -> step.id().equals(stepId))
            .findFirst()
            .orElse(null);
    }
    
    public List<Step> getIndependentSteps() {
        return steps.stream()
            .filter(step -> !step.hasDependencies())
            .toList();
    }
    
    @Override
    public String toString() {
        return "MultiToolPlan{" +
                "planType=" + planType +
                ", steps=" + steps.size() + " steps" +
                ", hasCondition=" + (conditionPrompt != null) +
                ", hasAggregation=" + (aggregationPrompt != null) +
                '}';
    }
}
