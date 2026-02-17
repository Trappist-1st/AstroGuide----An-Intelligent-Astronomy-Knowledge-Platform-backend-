package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息表实体，对应 messages 表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("messages")
public class Message {

    /** 消息ID */
    @TableId
    private String id;

    /** 所属会话ID */
    @TableField("conversation_id")
    private String conversationId;

    /** 角色：user | assistant | system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 难度档位：basic | intermediate | advanced */
    private String difficulty;

    /** 语言：en | zh */
    private String language;

    /** 状态：queued | streaming | done | error | cancelled */
    private String status;

    /** 错误码（status=error 时） */
    @TableField("error_code")
    private String errorCode;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 请求 token 数 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 回复 token 数 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 估算费用（美元） */
    @TableField("estimated_cost_usd")
    private Double estimatedCostUsd;

    /** 前端传入的幂等键，用于去重 */
    @TableField("client_message_id")
    private String clientMessageId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
