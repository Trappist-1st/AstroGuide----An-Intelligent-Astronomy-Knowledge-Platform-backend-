package com.imperium.astroguide.ai.tools;

import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;
import com.imperium.astroguide.service.WikipediaService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Spring AI Tool: Wikipedia search.
 */
@Component
public class WikipediaTool {

    private final WikipediaService wikipediaService;

    public WikipediaTool(WikipediaService wikipediaService) {
        this.wikipediaService = wikipediaService;
    }

    @Tool(name = "search_wikipedia",
            description = "Search Wikipedia for public astronomy background. Input should be a short query. "
                    + "Return referenceText plus citations.")
    public RagRetrieveResult searchWikipedia(
            @ToolParam(description = "Search query keywords, preferably in English") String query) {
        if (query == null || query.isBlank()) {
            return RagRetrieveResult.empty();
        }
        try {
            RagRetrieveResult result = wikipediaService.fetchForQuery(query);
            return result != null ? result : RagRetrieveResult.empty();
        } catch (Exception e) {
            return RagRetrieveResult.builder()
                    .referenceText("")
                    .citations(new ArrayList<>())
                    .build();
        }
    }
}
