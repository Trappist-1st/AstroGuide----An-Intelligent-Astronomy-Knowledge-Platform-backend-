package com.imperium.astroguide.ai.runtime;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次 Agent Run 取消信号（SSE 断开时设置；Workflow ReAct 循环轮询）。
 * <p>
 * 连接与 Run 同实例处理（SSE 粘性），进程内 Map 即可；Run 结束自动清理。
 */
@Component
public class AgentRunCancellationRegistry {

    private final Map<String, Boolean> cancelledRuns = new ConcurrentHashMap<>();

    public void register(String runId) {
        if (runId != null && !runId.isBlank()) {
            cancelledRuns.put(runId, Boolean.FALSE);
        }
    }

    public void cancel(String runId) {
        if (runId != null && !runId.isBlank()) {
            cancelledRuns.put(runId, Boolean.TRUE);
        }
    }

    public boolean isCancelled(String runId) {
        return runId != null && Boolean.TRUE.equals(cancelledRuns.get(runId));
    }

    public void unregister(String runId) {
        if (runId != null) {
            cancelledRuns.remove(runId);
        }
    }
}
