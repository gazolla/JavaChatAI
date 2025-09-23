package com.gazapps.inference;

import java.util.List;
import java.util.Map;

public record Step(
    String id,                          
    String serverId,                    
    String toolName,                    
    Map<String, Object> parameters,     
    List<String> dependencies          
) {
    
     public boolean isValid() {
        return id != null && !id.trim().isEmpty() &&
               serverId != null && !serverId.trim().isEmpty() &&
               toolName != null && !toolName.trim().isEmpty() &&
               parameters != null &&
               dependencies != null;
    }
    
     public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }
    
    public boolean dependsOn(String stepId) {
        return dependencies.contains(stepId);
    }
}
