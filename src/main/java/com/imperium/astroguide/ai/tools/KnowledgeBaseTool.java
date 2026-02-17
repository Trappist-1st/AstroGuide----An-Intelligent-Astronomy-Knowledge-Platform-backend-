package com.imperium.astroguide.ai.tools;

import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI Tool: Knowledge base search.
 */
@Component
public class KnowledgeBaseTool {

    @Nullable
    private final VectorStore vectorStore;

    public KnowledgeBaseTool(@Nullable VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(name = "search_knowledge_base",
            description = "Search the internal astronomy knowledge base (VectorStore) for factual evidence and excerpts. "
                    + "Use this for course/KB facts. Return referenceText plus citations.")
    public RagRetrieveResult searchKnowledgeBase(
            @ToolParam(description = "User question or a condensed query") String query,
            @ToolParam(required = false, description = "TopK results, default 8") Integer topK) {
        if (query == null || query.isBlank() || vectorStore == null) {
            return RagRetrieveResult.empty();
        }

        int k = (topK != null && topK > 0) ? topK : 8;
        SearchRequest searchRequest = SearchRequest.builder().query(query).topK(k).build();
        List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(searchRequest);
        if (docs == null || docs.isEmpty()) {
            return RagRetrieveResult.empty();
        }

        StringBuilder ref = new StringBuilder();
        List<CitationDto> citations = new ArrayList<>();

        for (int i = 0; i < docs.size(); i++) {
            org.springframework.ai.document.Document d = docs.get(i);
            if (d == null) continue;
            String text = d.getText() != null ? d.getText() : "";
            if (text.isBlank()) continue;
            String chunkId = d.getId() != null ? d.getId() : ("kb_" + i);

            String source = null;
            Map<String, Object> meta = d.getMetadata() != null ? d.getMetadata() : new HashMap<>();
            Object src = meta.get("source");
            if (src != null) source = String.valueOf(src);
            if (source == null || source.isBlank()) {
                source = "KnowledgeBase";
            }

            String excerpt = text.length() > 300 ? text.substring(0, 300) + "..." : text;
            citations.add(new CitationDto(chunkId, source, excerpt));

            ref.append("[KB-" + (i + 1) + "] ");
            ref.append(excerpt);
            ref.append("\n");
        }

        return RagRetrieveResult.builder()
                .referenceText(ref.toString())
                .citations(citations)
                .build();
    }
}
