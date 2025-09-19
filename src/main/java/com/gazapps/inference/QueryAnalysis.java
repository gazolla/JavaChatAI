package com.gazapps.inference;

import java.util.Map;

record QueryAnalysis(
	    ExecutionType execution,
	    String details, 
	    Map<String, Object> parameters 
	) {
	    enum ExecutionType { DIRECT_ANSWER, SINGLE_TOOL, MULTI_TOOL }
	}