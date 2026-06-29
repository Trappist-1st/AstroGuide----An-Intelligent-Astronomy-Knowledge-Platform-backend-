package com.imperium.astroguide.ai.tool;

import com.imperium.astroguide.ai.tools.ConceptCardTool;
import com.imperium.astroguide.ai.tools.KnowledgeBaseTool;
import com.imperium.astroguide.ai.tools.WikipediaTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Tool 注册中心：集中管理 Tool 元数据，并输出带 Policy 包装的可执行回调。
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, ToolCallback> rawCallbacks = new LinkedHashMap<>();
    private final Executor toolExecutor;

    @Value("${app.ai.tools.enabled:true}")
    private boolean toolsEnabled;

    public ToolRegistry(WikipediaTool wikipediaTool,
            KnowledgeBaseTool knowledgeBaseTool,
            ConceptCardTool conceptCardTool,
            @Qualifier("toolTaskExecutor") Executor toolExecutor,
            @Value("${app.ai.tools.timeout-ms:3000}") int defaultTimeoutMs,
            @Value("${app.ai.tools.max-calls-per-tool:2}") int defaultMaxCallsPerTool) {
        this.toolExecutor = toolExecutor;
        registerFromObject(wikipediaTool, ToolDefinition.builder()
                .name("search_wikipedia")
                .description("Wikipedia astronomy background search")
                .enabled(true)
                .timeoutMs(defaultTimeoutMs)
                .maxCallsPerRun(defaultMaxCallsPerTool)
                .sourceBean("wikipediaTool")
                .build());
        registerFromObject(knowledgeBaseTool, ToolDefinition.builder()
                .name("search_knowledge_base")
                .description("Internal astronomy knowledge base search")
                .enabled(true)
                .timeoutMs(defaultTimeoutMs)
                .maxCallsPerRun(defaultMaxCallsPerTool)
                .sourceBean("knowledgeBaseTool")
                .build());
        registerFromObject(conceptCardTool, ToolDefinition.builder()
                .name("lookup_concept_card")
                .description("Concept card lookup for terms and symbols")
                .enabled(true)
                .timeoutMs(defaultTimeoutMs)
                .maxCallsPerRun(defaultMaxCallsPerTool)
                .sourceBean("conceptCardTool")
                .build());
    }

    private void registerFromObject(Object toolObject, ToolDefinition definition) {
        for (ToolCallback callback : ToolCallbacks.from(toolObject)) {
            String toolName = callback.getToolDefinition().name();
            definitions.put(toolName, definition);
            rawCallbacks.put(toolName, callback);
        }
    }

    public boolean isToolsEnabled() {
        return toolsEnabled;
    }

    /** 带超时/预算策略的 Tool 回调，供 LangGraph ReAct 子图注册。 */
    public List<ToolCallback> policyAwareCallbacks() {
        if (!toolsEnabled) {
            return List.of();
        }
        List<ToolCallback> wrapped = new ArrayList<>();
        for (Map.Entry<String, ToolCallback> entry : rawCallbacks.entrySet()) {
            ToolDefinition definition = definitions.get(entry.getKey());
            wrapped.add(new PolicyEnforcingToolCallback(entry.getValue(), definition, toolExecutor));
        }
        return wrapped;
    }

    public Optional<ToolDefinition> findDefinition(String toolName) {
        return Optional.ofNullable(definitions.get(toolName));
    }

    public Map<String, ToolDefinition> allDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }
}
