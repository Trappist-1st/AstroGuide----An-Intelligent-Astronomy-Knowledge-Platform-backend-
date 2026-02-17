package com.imperium.astroguide.ai.tools;

import com.imperium.astroguide.model.dto.response.ConceptCardResponse;
import com.imperium.astroguide.service.ConceptCardService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring AI Tool: Concept card lookup.
 */
@Component
public class ConceptCardTool {

    private final ConceptCardService conceptCardService;

    public ConceptCardTool(ConceptCardService conceptCardService) {
        this.conceptCardService = conceptCardService;
    }

    @Tool(name = "lookup_concept_card",
            description = "Lookup a cached concept card (term/symbol definition) for stable explanations. "
                    + "Use type=term or sym; lang=en or zh; key is optional; text is the displayed term.")
    public Map<String, Object> lookupConceptCard(
            @ToolParam(description = "term|sym") String type,
            @ToolParam(description = "en|zh") String lang,
            @ToolParam(required = false, description = "Optional cache key, e.g. chandrasekhar_limit") String key,
            @ToolParam(required = false, description = "Optional display text for lookup, e.g. Chandrasekhar limit") String text) {
        ConceptCardResponse card = conceptCardService.lookup(type, lang, key, text);
        Map<String, Object> out = new HashMap<>();
        out.put("found", card != null);
        out.put("type", type);
        out.put("lang", lang);
        out.put("key", key);
        out.put("text", text);
        out.put("card", card);
        return out;
    }
}
