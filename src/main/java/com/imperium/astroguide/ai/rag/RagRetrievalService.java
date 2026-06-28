package com.imperium.astroguide.ai.rag;

import com.imperium.astroguide.ai.context.ContextTrimPolicy;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索：向量召回 + citation 组装（与 Tool 路径分离，避免双检索）。
 */
@Service
public class RagRetrievalService {

    @Nullable
    private final VectorStore vectorStore;

    @Value("${app.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${app.rag.top-k:8}")
    private int ragTopK;

    public RagRetrievalService(@Nullable VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public RagRetrievalResult retrieve(String userText) {
        if (!ragEnabled || vectorStore == null || userText == null || userText.isBlank()) {
            return RagRetrievalResult.empty();
        }

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(userText).topK(ragTopK).build());
        if (CollectionUtils.isEmpty(docs)) {
            return RagRetrievalResult.empty();
        }

        StringBuilder ref = new StringBuilder();
        List<CitationDto> citations = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                continue;
            }
            Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
            String source = meta.get("source") != null ? meta.get("source").toString() : "KnowledgeBase";
            String chunkId = doc.getId() != null ? doc.getId()
                    : (meta.get("chunk_id") != null ? meta.get("chunk_id").toString() : "chunk_" + i);
            String excerpt = truncate(doc.getText(), ContextTrimPolicy.DEFAULT_MAX_RAG_EXCERPT_CHARS);
            citations.add(new CitationDto(chunkId, source, excerpt));
            ref.append("[KB-").append(i + 1).append("] ").append(excerpt).append("\n");
        }
        return new RagRetrievalResult(ref.toString(), citations);
    }

    private static String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
