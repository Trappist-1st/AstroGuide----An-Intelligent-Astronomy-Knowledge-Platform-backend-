package com.imperium.astroguide.ai.tool;

import com.imperium.astroguide.ai.context.ContextTrimPolicy;
import com.imperium.astroguide.ai.runtime.AgentStreamEvent;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 带超时、预算与审计的 ToolCallback 装饰器。
 */
public final class PolicyEnforcingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final com.imperium.astroguide.ai.tool.ToolDefinition policyDefinition;
    private final Executor toolExecutor;

    public PolicyEnforcingToolCallback(ToolCallback delegate,
            com.imperium.astroguide.ai.tool.ToolDefinition policyDefinition,
            Executor toolExecutor) {
        this.delegate = delegate;
        this.policyDefinition = policyDefinition;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        ChatRunContext ctx = ChatRunContext.requireCurrent();
        String toolName = delegate.getToolDefinition().name();

        if (!ctx.toolPolicyService().allowCall(toolName, ctx.totalToolCalls(), ctx.callsByName(), policyDefinition)) {
            ToolExecutionRecord rejected = ToolExecutionRecord.builder()
                    .toolName(toolName)
                    .arguments(truncate(toolInput, 200))
                    .success(false)
                    .latencyMs(0L)
                    .resultPreview("")
                    .errorMessage("Tool budget exceeded")
                    .build();
            ctx.emit(new AgentStreamEvent.ToolFinished(rejected));
            return "{\"error\":\"Tool budget exceeded for " + toolName + "\"}";
        }

        ctx.emit(new AgentStreamEvent.ToolStarted(toolName, truncate(toolInput, 200)));
        long startMs = System.currentTimeMillis();
        try {
            String result = CompletableFuture
                    .supplyAsync(() -> delegate.call(toolInput), toolExecutor)
                    .orTimeout(policyDefinition.getTimeoutMs(), TimeUnit.MILLISECONDS)
                    .join();
            ctx.recordToolCall(toolName);
            long latencyMs = System.currentTimeMillis() - startMs;
            String preview = truncate(result, ContextTrimPolicy.DEFAULT_MAX_TOOL_RESULT_CHARS);
            ctx.emit(new AgentStreamEvent.ToolFinished(ToolExecutionRecord.builder()
                    .toolName(toolName)
                    .arguments(truncate(toolInput, 200))
                    .success(true)
                    .latencyMs(latencyMs)
                    .resultPreview(preview)
                    .build()));
            return preview;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            String error = e.getCause() instanceof TimeoutException ? "Tool timeout" : e.getMessage();
            ctx.emit(new AgentStreamEvent.ToolFinished(ToolExecutionRecord.builder()
                    .toolName(toolName)
                    .arguments(truncate(toolInput, 200))
                    .success(false)
                    .latencyMs(latencyMs)
                    .resultPreview("")
                    .errorMessage(error)
                    .build()));
            return "{\"error\":\"" + escapeJson(error) + "\"}";
        }
    }

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
