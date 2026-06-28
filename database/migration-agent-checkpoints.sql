-- Phase 2: LangGraph Checkpoint 持久化（已有库迁移）
USE astroguide;

CREATE TABLE IF NOT EXISTS `agent_checkpoints` (
  `id` VARCHAR(64) NOT NULL COMMENT '行ID',
  `thread_id` VARCHAR(128) NOT NULL COMMENT 'LangGraph threadId (conversationId:runId)',
  `checkpoint_id` VARCHAR(64) NOT NULL COMMENT 'Checkpoint UUID',
  `node_id` VARCHAR(64) NOT NULL COMMENT '当前 node',
  `next_node_id` VARCHAR(64) NOT NULL COMMENT '下一个 node',
  `state_json` LONGTEXT NOT NULL COMMENT 'AgentState JSON',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_agent_checkpoints_thread_created` (`thread_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LangGraph Checkpoint 快照';
