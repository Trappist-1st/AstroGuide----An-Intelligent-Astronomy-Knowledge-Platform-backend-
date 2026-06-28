package com.imperium.astroguide.ai.tool;

import lombok.Builder;
import lombok.Value;

/**
 * 单次 Tool 执行审计记录。
 */
@Value
@Builder
public class ToolExecutionRecord {

    String toolName;
    String arguments;
    boolean success;
    long latencyMs;
    String resultPreview;
    String errorMessage;

}
