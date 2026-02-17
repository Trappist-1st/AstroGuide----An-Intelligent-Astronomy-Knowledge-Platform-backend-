package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话表实体，对应 conversations 表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("conversations")
public class Conversation {

    /** 会话ID */
    @TableId
    private String id;

    /** 会话标题（可取自首条用户消息摘要） */
    private String title;

    /** 客户端/匿名用户标识（如 device_id 或 session_id） */
    @TableField("client_id")
    private String clientId;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
