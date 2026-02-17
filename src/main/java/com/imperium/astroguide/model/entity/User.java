package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户表实体，对应 users 表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("users")
public class User {

    /** 用户ID */
    @TableId
    private String id;

    /** 用户名，唯一 */
    private String username;

    /** 密码哈希，存储加密后的密码 */
    @TableField("password_hash")
    private String passwordHash;

    /** 邮箱 */
    private String email;

    /** 状态：active | disabled */
    private String status;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
