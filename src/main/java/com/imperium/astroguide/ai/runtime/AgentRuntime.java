package com.imperium.astroguide.ai.runtime;

import reactor.core.publisher.Flux;

/**
 * Agent Runtime 入口：LangGraph 驱动的单次 Agent 执行。
 */
public interface AgentRuntime {

    Flux<AgentStreamEvent> stream(AgentRunRequest request);
}
