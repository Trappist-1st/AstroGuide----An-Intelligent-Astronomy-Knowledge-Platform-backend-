package com.imperium.astroguide.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;
import com.imperium.astroguide.service.WikipediaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * V1 Wikipedia 按需：MediaWiki REST API 搜索 + 摘要，拼入参考区与 citations。
 * 请求头必须带 User-Agent，否则可能被限流。
 */
@Service
public class WikipediaServiceImpl implements WikipediaService {

    private static final String WIKI_SEARCH_URL = "https://en.wikipedia.org/w/rest.php/v1/search/page";
    private static final String USER_AGENT = "AstroGuide/1.0 (https://github.com/imperium/astroguide; contact@example.com)";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.wikipedia-on-demand.max-results:2}")
    private int maxResults;

    @Value("${app.rag.wikipedia-on-demand.max-chars-per-result:500}")
    private int maxCharsPerResult;

    public WikipediaServiceImpl(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    @Override
    public RagRetrieveResult fetchForQuery(String query) {
        if (query == null || query.isBlank()) {
            return RagRetrieveResult.empty();
        }

        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            encodedQuery = query.trim().replace(" ", "+");
        }

        String url = WIKI_SEARCH_URL + "?q=" + encodedQuery + "&limit=" + maxResults;
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return RagRetrieveResult.empty();
            }
            return parseSearchResponse(response.getBody());
        } catch (Exception e) {
            return RagRetrieveResult.empty();
        }
    }

    private RagRetrieveResult parseSearchResponse(String json) {
        List<CitationDto> citations = new ArrayList<>();
        StringBuilder referenceText = new StringBuilder();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode pages = root.get("pages");
            if (pages == null || !pages.isArray()) {
                return RagRetrieveResult.empty();
            }

            for (JsonNode page : pages) {
                String title = page.has("title") ? page.get("title").asText() : null;
                String excerpt = page.has("excerpt") ? page.get("excerpt").asText() : "";
                if (title == null || title.isBlank()) continue;

                String source = "Wikipedia: " + title;
                String chunkId = "wiki_" + title.replace(" ", "_") + "_" + citations.size();
                if (excerpt.length() > maxCharsPerResult) {
                    excerpt = excerpt.substring(0, maxCharsPerResult) + "...";
                }

                citations.add(CitationDto.builder()
                        .chunkId(chunkId)
                        .source(source)
                        .excerpt(excerpt)
                        .build());

                referenceText.append(excerpt).append("\n\n");
            }
        } catch (Exception e) {
            return RagRetrieveResult.empty();
        }

        return RagRetrieveResult.builder()
                .referenceText(referenceText.toString().trim())
                .citations(citations)
                .build();
    }
}
