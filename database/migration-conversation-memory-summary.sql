-- Phase 3: Summary Memory 表（已有库迁移）
USE astroguide;

CREATE TABLE IF NOT EXISTS `conversation_memory_summary` (
  `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `summary` TEXT NOT NULL COMMENT '滚动摘要',
  `message_count` INT DEFAULT NULL COMMENT '摘要覆盖消息数',
  `version` INT NOT NULL DEFAULT 1 COMMENT '摘要版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`conversation_id`),
  CONSTRAINT `fk_memory_summary_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话级摘要记忆';
