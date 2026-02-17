package com.imperium.astroguide.service;



/**
 * 请求用量记录服务（可观测性），对应 TDD 7、11。
 */
public interface UsageService {

    /**
     * 记录一次流式请求的用量与耗时。
     *
     * @param messageId        assistant 消息 ID
     * @param model            模型名
     * @param latencyMs        耗时（毫秒）
     * @param promptTokens     请求 token 数（可为 null，表示未统计）
     * @param completionTokens 回复 token 数（可为 null）
     * @param estimatedCostUsd 估算费用（美元，可为 null）
     */
    void record(String messageId, String model, int latencyMs,
                Integer promptTokens, Integer completionTokens, Double estimatedCostUsd);
}
