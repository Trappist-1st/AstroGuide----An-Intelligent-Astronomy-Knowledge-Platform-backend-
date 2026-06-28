package com.imperium.astroguide.ai.graph;

import com.imperium.astroguide.ai.tool.ChatRunContext;
import org.bsc.langgraph4j.spring.ai.agent.ReactAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LangGraph ChatService：Context 由 Workflow 显式组装；maxTokens 从 {@link ChatRunContext} 读取。
 */
public final class AstroGuideChatService implements ReactAgent.ChatService {

    private final ChatClient chatClient;

    public AstroGuideChatService(ChatModel chatModel, List<ToolCallback> tools) {
        var toolOptions = ToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
        var clientBuilder = ChatClient.builder(chatModel).defaultOptions(toolOptions);
        if (tools != null && !tools.isEmpty()) {
            clientBuilder.defaultToolCallbacks(tools);
        }
        this.chatClient = clientBuilder.build();
    }

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }

    @Override
    public ChatResponse execute(List<Message> messages) {
        var spec = chatClient.prompt().messages(messages);
        spec = applyMaxTokens(spec);
        return spec.call().chatResponse();
    }

    @Override
    public Flux<ChatResponse> streamingExecute(List<Message> messages) {
        var spec = chatClient.prompt().messages(messages);
        spec = applyMaxTokens(spec);
        return spec.stream().chatResponse();
    }

    private static ChatClient.ChatClientRequestSpec applyMaxTokens(ChatClient.ChatClientRequestSpec spec) {
        try {
            ChatRunContext ctx = ChatRunContext.requireCurrent();
            Integer maxTokens = ctx.maxCompletionTokens();
            if (maxTokens != null && maxTokens > 0) {
                return spec.options(OpenAiChatOptions.builder().maxTokens(maxTokens).build());
            }
        } catch (IllegalStateException ignored) {
            // 非 Agent Run 线程
        }
        return spec;
    }
}
