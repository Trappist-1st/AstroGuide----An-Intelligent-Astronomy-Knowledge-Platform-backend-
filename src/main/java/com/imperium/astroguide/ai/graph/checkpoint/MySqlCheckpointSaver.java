package com.imperium.astroguide.ai.graph.checkpoint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.mapper.AgentCheckpointMapper;
import com.imperium.astroguide.model.entity.AgentCheckpoint;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LangGraph Checkpoint 持久化到 MySQL，支持按 threadId 恢复执行快照。
 */
@Component
public class MySqlCheckpointSaver implements BaseCheckpointSaver {

    private final AgentCheckpointMapper checkpointMapper;
    private final ObjectMapper objectMapper;

    public MySqlCheckpointSaver(AgentCheckpointMapper checkpointMapper, ObjectMapper objectMapper) {
        this.checkpointMapper = checkpointMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = threadId(config);
        return checkpointMapper.selectList(new LambdaQueryWrapper<AgentCheckpoint>()
                        .eq(AgentCheckpoint::getThreadId, threadId)
                        .orderByAsc(AgentCheckpoint::getCreatedAt))
                .stream()
                .map(this::toCheckpoint)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        String threadId = threadId(config);
        AgentCheckpoint row = checkpointMapper.selectOne(new LambdaQueryWrapper<AgentCheckpoint>()
                .eq(AgentCheckpoint::getThreadId, threadId)
                .orderByDesc(AgentCheckpoint::getCreatedAt)
                .last("LIMIT 1"));
        return row == null ? Optional.empty() : Optional.of(toCheckpoint(row));
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        AgentCheckpoint row = new AgentCheckpoint();
        row.setId("cp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        row.setThreadId(threadId(config));
        row.setCheckpointId(checkpoint.getId());
        row.setNodeId(checkpoint.getNodeId());
        row.setNextNodeId(checkpoint.getNextNodeId());
        row.setStateJson(objectMapper.writeValueAsString(checkpoint.getState()));
        row.setCreatedAt(LocalDateTime.now());
        checkpointMapper.insert(row);
        return config;
    }

    @Override
    public Tag release(RunnableConfig config) {
        String threadId = threadId(config);
        List<Checkpoint> released = checkpointMapper.selectList(new LambdaQueryWrapper<AgentCheckpoint>()
                        .eq(AgentCheckpoint::getThreadId, threadId)
                        .orderByAsc(AgentCheckpoint::getCreatedAt))
                .stream()
                .map(this::toCheckpoint)
                .collect(Collectors.toList());
        checkpointMapper.delete(new LambdaQueryWrapper<AgentCheckpoint>()
                .eq(AgentCheckpoint::getThreadId, threadId));
        return new Tag(threadId, released);
    }

    private Checkpoint toCheckpoint(AgentCheckpoint row) {
        try {
            Map<String, Object> state = objectMapper.readValue(row.getStateJson(), new TypeReference<>() {
            });
            return Checkpoint.builder()
                    .id(row.getCheckpointId())
                    .state(state)
                    .nodeId(row.getNodeId())
                    .nextNodeId(row.getNextNodeId())
                    .build();
        } catch (Exception e) {
            return Checkpoint.builder()
                    .id(row.getCheckpointId())
                    .state(new LinkedHashMap<>())
                    .nodeId(row.getNodeId())
                    .nextNodeId(row.getNextNodeId())
                    .build();
        }
    }
}
