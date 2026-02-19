package com.imperium.astroguide.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供 {@link ChatClient} bean。
 * spring-ai-starter-model-openai 会自动配置 ChatModel 和 ChatClient.Builder，
 * 这里只需用 Builder 构建 ChatClient 实例。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
