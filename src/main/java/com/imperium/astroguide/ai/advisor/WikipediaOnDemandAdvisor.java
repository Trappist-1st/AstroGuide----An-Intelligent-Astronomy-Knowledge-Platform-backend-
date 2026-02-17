package com.imperium.astroguide.ai.advisor;

import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;
import com.imperium.astroguide.service.WikipediaService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wikipedia 按需实时拉取 Advisor：
 * - 在 ChatClientRequest context 中读取 {@link #ORIGINAL_QUERY} 作为检索 query
 * - 调用 {@link WikipediaService} 获取摘要片段
 * - 将检索到的片段以 Documents 形式放入 context（key: {@link #RETRIEVED_DOCUMENTS}）
 * - 将参考片段追加到 user message（保持与 RAG 类似的参考块格式）
 */
public class WikipediaOnDemandAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String ORIGINAL_QUERY = "wiki_original_query";
    public static final String RETRIEVED_DOCUMENTS = "wiki_retrieved_documents";

    private final WikipediaService wikipediaService;
    private final int order;

    public WikipediaOnDemandAdvisor(WikipediaService wikipediaService, int order) {
        this.wikipediaService = wikipediaService;
        this.order = order;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientRequest updatedRequest = maybeAugmentWithWikipedia(chatClientRequest);
        return callAdvisorChain.nextCall(updatedRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        return Mono.just(chatClientRequest)
                .publishOn(Schedulers.boundedElastic())
                .map(this::maybeAugmentWithWikipedia)
                .flatMapMany(chain::nextStream);
    }

    private ChatClientRequest maybeAugmentWithWikipedia(ChatClientRequest chatClientRequest) {
        if (chatClientRequest == null || chatClientRequest.prompt() == null
                || chatClientRequest.prompt().getUserMessage() == null) {
            return chatClientRequest;
        }

        Object q = chatClientRequest.context() != null ? chatClientRequest.context().get(ORIGINAL_QUERY) : null;
        String query = q != null ? q.toString() : null;
        if (query == null || query.isBlank()) {
            return chatClientRequest;
        }

        RagRetrieveResult wiki = wikipediaService.fetchForQuery(query);
        if (wiki == null || wiki.getReferenceText() == null || wiki.getReferenceText().isBlank()) {
            return chatClientRequest;
        }

        List<Document> wikiDocs = toDocuments(wiki);
        if (wikiDocs.isEmpty()) {
            return chatClientRequest;
        }

        String userText = chatClientRequest.prompt().getUserMessage().getText();
        String augmentedUserText = "[参考]\n\n" + wiki.getReferenceText().trim()
            + "\n\n---\n\n" + (userText != null ? userText : "");

        Map<String, Object> ctx = new HashMap<>(chatClientRequest.context() != null ? chatClientRequest.context() : Map.of());
        ctx.put(RETRIEVED_DOCUMENTS, wikiDocs);

        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmentedUserText))
                .context(ctx)
                .build();
    }

    private static List<Document> toDocuments(RagRetrieveResult wiki) {
        List<Document> docs = new ArrayList<>();
        if (wiki.getCitations() == null) {
            return docs;
        }
        for (CitationDto c : wiki.getCitations()) {
            if (c == null) continue;
            String text = c.getExcerpt() != null ? c.getExcerpt() : "";
            if (text.isBlank()) continue;

            Map<String, Object> meta = new HashMap<>();
            if (c.getSource() != null) meta.put("source", c.getSource());
            if (c.getChunkId() != null) meta.put("chunk_id", c.getChunkId());

            docs.add(new Document(text, meta));
        }
        return docs;
    }
}
