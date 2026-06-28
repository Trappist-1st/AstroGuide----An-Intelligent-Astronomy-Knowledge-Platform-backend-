package com.imperium.astroguide.ai.tool;

import lombok.Builder;
import lombok.Value;

/**
 * Tool 元数据：注册中心统一管理名称、描述与治理策略。
 */
@Value
@Builder
public class ToolDefinition {

    String name;
    String description;
    boolean enabled;
    int timeoutMs;
    int maxCallsPerRun;
    String sourceBean;

}
