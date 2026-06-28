package com.imperium.astroguide.ai.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Tool 调用治理：总次数预算与单工具次数限制。
 */
@Service
public class ToolPolicyService {

    @Value("${app.ai.tools.max-calls-total:4}")
    private int maxCallsTotal;

    public boolean allowCall(String toolName, int currentTotal, Map<String, Integer> callsByName, ToolDefinition definition) {
        if (currentTotal >= maxCallsTotal) {
            return false;
        }
        if (definition == null || !definition.isEnabled()) {
            return false;
        }
        int perToolLimit = definition.getMaxCallsPerRun();
        int currentForTool = callsByName.getOrDefault(toolName, 0);
        return currentForTool < perToolLimit;
    }

    public int maxCallsTotal() {
        return maxCallsTotal;
    }
}
