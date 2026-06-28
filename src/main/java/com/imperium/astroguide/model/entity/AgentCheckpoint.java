package com.imperium.astroguide.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_checkpoints")
public class AgentCheckpoint {

    @TableId
    private String id;

    @TableField("thread_id")
    private String threadId;

    @TableField("checkpoint_id")
    private String checkpointId;

    @TableField("node_id")
    private String nodeId;

    @TableField("next_node_id")
    private String nextNodeId;

    @TableField("state_json")
    private String stateJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
