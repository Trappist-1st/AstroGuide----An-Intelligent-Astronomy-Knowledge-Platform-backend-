package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 单次执行审计记录（LangGraph Runtime）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_runs")
public class AgentRun {

    @TableId
    private String id;

    @TableField("request_id")
    private String requestId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("message_id")
    private String messageId;

    private String status;

    private String model;

    @TableField("tool_calls_json")
    private String toolCallsJson;

    @TableField("node_timings_json")
    private String nodeTimingsJson;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("termination_reason")
    private String terminationReason;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
