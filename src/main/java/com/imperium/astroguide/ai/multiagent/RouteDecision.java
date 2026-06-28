package com.imperium.astroguide.ai.multiagent;

/**
 * 问题路由结果，供 Workflow 与 SSE 可观测性使用。
 */
public record RouteDecision(
        RouteMode mode,
        String reasonCode,
        double confidence) {

    public static RouteDecision simple(String reasonCode, double confidence) {
        return new RouteDecision(RouteMode.SIMPLE, reasonCode, confidence);
    }

    public static RouteDecision complex(String reasonCode, double confidence) {
        return new RouteDecision(RouteMode.COMPLEX, reasonCode, confidence);
    }
}
