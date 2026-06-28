package com.imperium.astroguide.ai.tool;

import com.imperium.astroguide.ai.runtime.AgentStreamEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 单次 Agent Run 的 ThreadLocal 上下文：Tool 预算、maxTokens、事件回调。
 */
public final class ChatRunContext {

    private static final ThreadLocal<ChatRunContext> CURRENT = new ThreadLocal<>();

    private final String runId;
    private final Integer maxCompletionTokens;
    private final ToolPolicyService toolPolicyService;
    private final Consumer<AgentStreamEvent> eventConsumer;
    private final Map<String, Integer> callsByName = new ConcurrentHashMap<>();
    private volatile int totalToolCalls;

    private ChatRunContext(String runId,
            Integer maxCompletionTokens,
            ToolPolicyService toolPolicyService,
            Consumer<AgentStreamEvent> eventConsumer) {
        this.runId = runId;
        this.maxCompletionTokens = maxCompletionTokens;
        this.toolPolicyService = toolPolicyService;
        this.eventConsumer = eventConsumer;
    }

    public static ChatRunContext requireCurrent() {
        ChatRunContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("ChatRunContext not initialized for current thread");
        }
        return ctx;
    }

    public static <T> T run(String runId,
            Integer maxCompletionTokens,
            ToolPolicyService toolPolicyService,
            Consumer<AgentStreamEvent> eventConsumer,
            java.util.concurrent.Callable<T> action) throws Exception {
        ChatRunContext ctx = new ChatRunContext(runId, maxCompletionTokens, toolPolicyService, eventConsumer);
        CURRENT.set(ctx);
        try {
            return action.call();
        } finally {
            CURRENT.remove();
        }
    }

    public String runId() {
        return runId;
    }

    public Integer maxCompletionTokens() {
        return maxCompletionTokens;
    }

    public ToolPolicyService toolPolicyService() {
        return toolPolicyService;
    }

    public Map<String, Integer> callsByName() {
        return callsByName;
    }

    public int totalToolCalls() {
        return totalToolCalls;
    }

    public void recordToolCall(String toolName) {
        totalToolCalls++;
        callsByName.merge(toolName, 1, Integer::sum);
    }

    public void emit(AgentStreamEvent event) {
        if (eventConsumer != null) {
            eventConsumer.accept(event);
        }
    }
}
