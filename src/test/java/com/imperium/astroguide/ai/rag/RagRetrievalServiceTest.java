package com.imperium.astroguide.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagRetrievalServiceTest {

    @Test
    void retrieve_degradesWhenVectorStoreFails() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("qdrant down"));

        RagRetrievalService service = new RagRetrievalService(vectorStore);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ragEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ragTopK", 4);

        RagRetrievalResult result = service.retrieve("black hole");
        assertTrue(result.citations().isEmpty());
        assertTrue(result.referenceContext().isBlank());
    }

    @Test
    void retrieve_returnsCitationsWhenSuccessful() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document doc = new Document("id-1", "excerpt text", java.util.Map.of("source", "Book"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RagRetrievalService service = new RagRetrievalService(vectorStore);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ragEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ragTopK", 4);

        RagRetrievalResult result = service.retrieve("black hole");
        assertFalse(result.citations().isEmpty());
    }
}
