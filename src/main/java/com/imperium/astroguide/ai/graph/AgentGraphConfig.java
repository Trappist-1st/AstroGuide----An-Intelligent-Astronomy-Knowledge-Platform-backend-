package com.imperium.astroguide.ai.graph;

import com.imperium.astroguide.ai.tool.ToolRegistry;
import com.imperium.astroguide.ai.graph.checkpoint.MySqlCheckpointSaver;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangGraph ReAct 子图：agent ↔ tools 循环；Checkpoint 持久化到 MySQL。
 */
@Configuration
public class AgentGraphConfig {

    @Bean
    public CompiledGraph<AgentExecutor.State> reactCompiledGraph(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            MySqlCheckpointSaver checkpointSaver,
            @Value("${app.ai.runtime.recursion-limit:12}") int recursionLimit) throws GraphStateException {

        var builder = AgentExecutor.builder()
                .chatModel(chatModel)
                .streaming(true)
                .emitStreamingEnd(true);

        var toolCallbacks = toolRegistry.policyAwareCallbacks();
        for (ToolCallback callback : toolCallbacks) {
            builder.tool(callback);
        }

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(checkpointSaver)
                .recursionLimit(recursionLimit)
                .build();

        return builder.build(b -> new AstroGuideChatService(chatModel, toolCallbacks)).compile(compileConfig);
    }
}
