package com.imperium.astroguide.ai.runtime;

import com.imperium.astroguide.ai.tool.ToolExecutionRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 Agent Run 的可变观测上下文（node/tool 耗时与调用记录）。
 */
public class AgentRunContext {

    private final List<ToolExecutionRecord> toolExecutions = new ArrayList<>();
    private final Map<String, Long> nodeTimingsMs = new LinkedHashMap<>();
    private final Map<String, Long> nodeStartMs = new LinkedHashMap<>();
    private final AtomicInteger toolCallCount = new AtomicInteger();

    public void markNodeStart(String nodeId) {
        nodeStartMs.put(nodeId, System.currentTimeMillis());
    }

    public void markNodeEnd(String nodeId) {
        Long start = nodeStartMs.remove(nodeId);
        if (start != null) {
            nodeTimingsMs.put(nodeId, System.currentTimeMillis() - start);
        }
    }

    public void addToolExecution(ToolExecutionRecord record) {
        toolExecutions.add(record);
        toolCallCount.incrementAndGet();
    }

    public List<ToolExecutionRecord> toolExecutions() {
        return List.copyOf(toolExecutions);
    }

    public Map<String, Long> nodeTimingsMs() {
        return Map.copyOf(nodeTimingsMs);
    }

    public int toolCallCount() {
        return toolCallCount.get();
    }
}
