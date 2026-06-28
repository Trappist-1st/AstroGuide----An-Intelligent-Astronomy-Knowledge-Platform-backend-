package com.imperium.astroguide.ai.rag;

import com.imperium.astroguide.model.dto.rag.CitationDto;

import java.util.List;

public record RagRetrievalResult(String referenceContext, List<CitationDto> citations) {

    public static RagRetrievalResult empty() {
        return new RagRetrievalResult("", List.of());
    }

    public boolean hasContext() {
        return referenceContext != null && !referenceContext.isBlank();
    }
}
