package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 术语/公式概念卡片缓存表实体，对应 term_cache 表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("term_cache")
public class TermCache {

    /** 缓存键（如 type:identifier:language） */
    @TableId("key")
    private String cacheKey;

    /** 类型：term | sym */
    private String type;

    /** 语言：en | zh */
    private String language;

    /** 展示标题 */
    private String title;

    /** 概念卡片内容（JSON） */
    @TableField("payload_json")
    private String payloadJson;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
