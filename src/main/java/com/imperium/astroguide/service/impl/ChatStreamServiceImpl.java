package com.imperium.astroguide.service.impl;

import com.imperium.astroguide.ai.tools.ConceptCardTool;
import com.imperium.astroguide.ai.tools.KnowledgeBaseTool;
import com.imperium.astroguide.ai.tools.WikipediaTool;
import com.imperium.astroguide.service.ChatStreamService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatStreamService 实现：通过 Spring AI ChatClient 流式调用 LLM。
 * <p>
 * 所有能力开关（RAG、Tool Calling）均由配置文件控制，对外暴露简洁接口。
 */
@Service
public class ChatStreamServiceImpl implements ChatStreamService {

    private final ChatClient chatClient;

    @Nullable
    private final VectorStore vectorStore;

    private final ChatMemory chatMemory;

    private final WikipediaTool wikipediaTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final ConceptCardTool conceptCardTool;

    @Value("${app.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${app.rag.top-k:8}")
    private int ragTopK;

    @Value("${app.ai.tools.enabled:true}")
    private boolean toolsEnabled;

    public ChatStreamServiceImpl(ChatClient chatClient,
            @Nullable VectorStore vectorStore,
            ChatMemory chatMemory,
            WikipediaTool wikipediaTool,
            KnowledgeBaseTool knowledgeBaseTool,
            ConceptCardTool conceptCardTool) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        this.wikipediaTool = wikipediaTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.conceptCardTool = conceptCardTool;
    }

    @Override
    public Flux<ChatClientResponse> streamChatClientResponses(String conversationId,
            String systemPrompt,
            String userText,
            Integer maxCompletionTokens) {
        var promptSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userText);

        // 可选：限制最大输出 tokens
        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            promptSpec = promptSpec.options(
                    OpenAiChatOptions.builder().maxTokens(maxCompletionTokens).build());
        }

        // ---- Advisors ----
        List<Advisor> advisors = new ArrayList<>();

        // 对话记忆：由 Spring AI 自动管理上下文注入
        if (conversationId != null && !conversationId.isBlank()) {
            advisors.add(MessageChatMemoryAdvisor.builder(chatMemory)
                    .conversationId(conversationId)
                    .order(-100)
                    .build());
        }

        // RAG 向量检索：仅当本次查询能检索到文档时才注入 QuestionAnswerAdvisor；
        // 否则不注入，模型直接按系统提示用自身知识回答，避免「知识库为空却只让根据上下文回答」的悖论。
        if (ragEnabled && vectorStore != null && userText != null && !userText.isBlank()) {
            List<?> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(userText).topK(ragTopK).build());
            if (!CollectionUtils.isEmpty(docs)) {
                advisors.add(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(ragTopK).build())
                        .build());
            }
        }

        if (!advisors.isEmpty()) {
            promptSpec = promptSpec.advisors(advisors.toArray(new Advisor[0]));
        }

        // ---- Tool Calling ----
        // 由 Spring AI 框架托管：模型自主决定是否调用工具
        if (toolsEnabled) {
            promptSpec = promptSpec.tools(wikipediaTool, knowledgeBaseTool, conceptCardTool);
        }

        return promptSpec.stream().chatClientResponse();
    }
}
