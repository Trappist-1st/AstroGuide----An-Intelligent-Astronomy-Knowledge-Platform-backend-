package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话级摘要记忆（Summary Memory），由异步 Worker 滚动更新。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("conversation_memory_summary")
public class ConversationMemorySummary {

    @TableId
    private String conversationId;

    private String summary;

    @TableField("message_count")
    private Integer messageCount;

    @TableField("version")
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
