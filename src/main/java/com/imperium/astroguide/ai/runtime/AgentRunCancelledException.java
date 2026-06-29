package com.imperium.astroguide.ai.runtime;

/**
 * Agent Run 被取消时抛出，供 Workflow 中断 ReAct 循环。
 */
public class AgentRunCancelledException extends RuntimeException {

    public AgentRunCancelledException(String runId) {
        super("Agent run cancelled: " + runId);
    }
}
