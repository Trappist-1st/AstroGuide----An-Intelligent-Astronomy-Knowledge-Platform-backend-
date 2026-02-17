-- AstroGuide V0 数据库结构（MySQL）
-- 可直接在 MySQL 中执行以创建数据库与表（mysql -u root -p < schema-mysql.sql 或于客户端中执行）

CREATE DATABASE IF NOT EXISTS astroguide DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE astroguide;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS `request_usage`;
DROP TABLE IF EXISTS `messages`;
DROP TABLE IF EXISTS `term_cache`;
DROP TABLE IF EXISTS `conversations`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `id` VARCHAR(64) NOT NULL COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态：active | disabled',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 会话表
-- ----------------------------
CREATE TABLE `conversations` (
  `id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `title` VARCHAR(255) DEFAULT NULL COMMENT '会话标题（可取自首条用户消息摘要）',
  `client_id` VARCHAR(64) NOT NULL COMMENT '客户端/匿名用户标识（如 device_id 或 session_id）',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversations_client_updated` (`client_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ----------------------------
-- 消息表
-- ----------------------------
CREATE TABLE `messages` (
  `id` VARCHAR(64) NOT NULL COMMENT '消息ID',
  `conversation_id` VARCHAR(64) NOT NULL COMMENT '所属会话ID',
  `role` VARCHAR(16) NOT NULL COMMENT '角色：user | assistant | system',
  `content` LONGTEXT NOT NULL COMMENT '消息内容',
  `difficulty` VARCHAR(32) DEFAULT NULL COMMENT '难度档位：basic | intermediate | advanced',
  `language` VARCHAR(8) DEFAULT NULL COMMENT '语言：en | zh',
  `status` VARCHAR(32) NOT NULL COMMENT '状态：queued | streaming | done | error | cancelled',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码（status=error 时）',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `prompt_tokens` INT DEFAULT NULL COMMENT '请求 token 数',
  `completion_tokens` INT DEFAULT NULL COMMENT '回复 token 数',
  `estimated_cost_usd` DOUBLE DEFAULT NULL COMMENT '估算费用（美元）',
  `client_message_id` VARCHAR(64) DEFAULT NULL COMMENT '前端传入的幂等键，用于去重',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_messages_conv_created` (`conversation_id`, `created_at`),
  KEY `idx_messages_client_msgid` (`client_message_id`),
  CONSTRAINT `fk_messages_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ----------------------------
-- 术语/公式概念卡片缓存表（Concept Cards）
-- ----------------------------
CREATE TABLE `term_cache` (
  `key` VARCHAR(512) NOT NULL COMMENT '缓存键（如 type:identifier:language）',
  `type` VARCHAR(16) NOT NULL COMMENT '类型：term | sym',
  `language` VARCHAR(8) NOT NULL COMMENT '语言：en | zh',
  `title` VARCHAR(255) NOT NULL COMMENT '展示标题',
  `payload_json` TEXT NOT NULL COMMENT '概念卡片内容（JSON）',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`key`),
  KEY `idx_term_cache_type_lang` (`type`, `language`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='术语/公式概念卡片缓存';

-- ----------------------------
-- 请求用量/统计表（可观测性）
-- ----------------------------
CREATE TABLE `request_usage` (
  `id` VARCHAR(64) NOT NULL COMMENT '记录ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT '关联的 assistant 消息ID',
  `model` VARCHAR(64) NOT NULL COMMENT '调用的模型名',
  `latency_ms` INT NOT NULL COMMENT '请求耗时（毫秒）',
  `prompt_tokens` INT DEFAULT NULL COMMENT '请求 token 数',
  `completion_tokens` INT DEFAULT NULL COMMENT '回复 token 数',
  `estimated_cost_usd` DOUBLE DEFAULT NULL COMMENT '估算费用（美元）',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_usage_message` (`message_id`),
  CONSTRAINT `fk_usage_message` FOREIGN KEY (`message_id`) REFERENCES `messages` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请求用量与耗时统计';

SET FOREIGN_KEY_CHECKS = 1;

-- 若已有库未包含 estimated_cost_usd，可执行以下迁移（V0 可观测性与成本估算）：
-- ALTER TABLE `messages` ADD COLUMN `estimated_cost_usd` DOUBLE DEFAULT NULL COMMENT '估算费用（美元）' AFTER `completion_tokens`;
-- ALTER TABLE `request_usage` ADD COLUMN `estimated_cost_usd` DOUBLE DEFAULT NULL COMMENT '估算费用（美元）' AFTER `completion_tokens`;
