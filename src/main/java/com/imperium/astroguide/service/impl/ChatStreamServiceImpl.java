package com.imperium.astroguide.service.impl;

import com.imperium.astroguide.ai.advisor.WikipediaOnDemandAdvisor;
import com.imperium.astroguide.ai.tools.ConceptCardTool;
import com.imperium.astroguide.ai.tools.KnowledgeBaseTool;
import com.imperium.astroguide.ai.tools.WikipediaTool;
import com.imperium.astroguide.service.ChatStreamService;
import com.imperium.astroguide.service.WikipediaService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatStreamServiceImpl implements ChatStreamService {

    private final ChatClient chatClient;

    @Nullable
    private final VectorStore vectorStore;

    private final WikipediaService wikipediaService;

    private final ChatMemory chatMemory;

    private final WikipediaTool wikipediaTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final ConceptCardTool conceptCardTool;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModel;

    @Value("${app.rag.top-k:8}")
    private int ragTopK;

    public ChatStreamServiceImpl(ChatClient.Builder chatClientBuilder,
                                 @Nullable VectorStore vectorStore,
                                 WikipediaService wikipediaService,
                                 ChatMemory chatMemory,
                                 WikipediaTool wikipediaTool,
                                 KnowledgeBaseTool knowledgeBaseTool,
                                 ConceptCardTool conceptCardTool) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.wikipediaService = wikipediaService;
        this.chatMemory = chatMemory;
        this.wikipediaTool = wikipediaTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.conceptCardTool = conceptCardTool;
    }

    @Override
    public Flux<String> streamContent(String systemPrompt, String userContent, String model) {
        return streamContent(systemPrompt, userContent, model, null);
    }

    @Override
    public Flux<String> streamContent(String systemPrompt, String userContent, String model, Integer maxCompletionTokens) {
        var promptSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userContent);
        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            promptSpec = promptSpec.options(
                    OpenAiChatOptions.builder().maxTokens(maxCompletionTokens).build());
        }
        return promptSpec.stream().content();
    }

    @Override
    public Flux<ChatClientResponse> streamChatClientResponses(String conversationId,
                                                             String systemPrompt,
                                                             String userText,
                                                             String model,
                                                             Integer maxCompletionTokens,
                                                             boolean ragEnabled,
                                                             boolean wikipediaOnDemandEnabled,
                                                             boolean toolCallingEnabled,
                                                             boolean wikipediaToolEnabled,
                                                             boolean knowledgeBaseToolEnabled,
                                                             boolean conceptCardToolEnabled,
                                                             Map<String, Object> advisorParams) {
        var promptSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userText);

        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            promptSpec = promptSpec.options(OpenAiChatOptions.builder().maxTokens(maxCompletionTokens).build());
        }

        List<Advisor> advisors = new ArrayList<>();

        if (conversationId != null && !conversationId.isBlank()) {
            advisors.add(MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(conversationId)
                .order(-100)
                .build());
        }

        if (ragEnabled && vectorStore != null) {
            var searchRequest = SearchRequest.builder().topK(ragTopK).build();
            advisors.add(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(searchRequest)
                    .build());
        }

        if (wikipediaOnDemandEnabled) {
            advisors.add(new WikipediaOnDemandAdvisor(wikipediaService, 10));
        }

        if (!advisors.isEmpty()) {
            promptSpec = promptSpec.advisors(advisors.toArray(new Advisor[0]));
        }
        if (advisorParams != null && !advisorParams.isEmpty()) {
            promptSpec = promptSpec.advisors(a -> a.params(advisorParams));
        }

        if (toolCallingEnabled) {
            List<Object> tools = new ArrayList<>();
            if (wikipediaToolEnabled) {
                tools.add(wikipediaTool);
            }
            if (knowledgeBaseToolEnabled) {
                tools.add(knowledgeBaseTool);
            }
            if (conceptCardToolEnabled) {
                tools.add(conceptCardTool);
            }
            if (!tools.isEmpty()) {
                promptSpec = promptSpec.tools(tools.toArray());
            }
        }

        return promptSpec.stream().chatClientResponse();
    }
}
