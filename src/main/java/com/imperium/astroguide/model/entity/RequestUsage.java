package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 请求用量/统计表实体，对应 request_usage 表（可观测性）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("request_usage")
public class RequestUsage {

    /** 记录ID */
    @TableId
    private String id;

    /** 关联的 assistant 消息ID */
    @TableField("message_id")
    private String messageId;

    /** 调用的模型名 */
    private String model;

    /** 请求耗时（毫秒） */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 请求 token 数 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 回复 token 数 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 估算费用（美元） */
    @TableField("estimated_cost_usd")
    private Double estimatedCostUsd;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
