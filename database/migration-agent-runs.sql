-- Phase 1: LangGraph Agent Runtime 审计表（已有库迁移）
USE astroguide;

CREATE TABLE IF NOT EXISTS `agent_runs` (
  `id` VARCHAR(64) NOT NULL COMMENT 'runId',
  `request_id` VARCHAR(64) DEFAULT NULL COMMENT 'X-Request-Id',
  `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT 'user 消息ID',
  `status` VARCHAR(32) NOT NULL COMMENT 'running|completed|failed|cancelled',
  `model` VARCHAR(64) DEFAULT NULL COMMENT '模型名',
  `tool_calls_json` TEXT DEFAULT NULL COMMENT 'Tool 调用审计 JSON',
  `node_timings_json` TEXT DEFAULT NULL COMMENT 'Node 耗时 JSON',
  `prompt_tokens` INT DEFAULT NULL COMMENT 'prompt tokens',
  `completion_tokens` INT DEFAULT NULL COMMENT 'completion tokens',
  `latency_ms` INT DEFAULT NULL COMMENT '总耗时 ms',
  `termination_reason` VARCHAR(64) DEFAULT NULL COMMENT '终止原因',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `created_at` DATETIME(3) NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_runs_conv_created` (`conversation_id`, `created_at`),
  KEY `idx_agent_runs_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Runtime 单次执行审计';
