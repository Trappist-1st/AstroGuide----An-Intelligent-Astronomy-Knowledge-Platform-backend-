package com.imperium.astroguide.ai.multiagent;

/**
 * Multi-Agent 路由模式：简单问题走单 Agent ReAct，复杂问题走 Plan + ReAct + Review。
 */
public enum RouteMode {
    SIMPLE,
    COMPLEX
}
