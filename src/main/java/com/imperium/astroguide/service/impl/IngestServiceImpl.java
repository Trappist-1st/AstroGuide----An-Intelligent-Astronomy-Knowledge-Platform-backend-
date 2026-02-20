package com.imperium.astroguide.service.impl;

import com.imperium.astroguide.ingest.TextChunker;
import com.imperium.astroguide.ingest.parser.DocumentParseResult;
import com.imperium.astroguide.ingest.parser.DocumentParserService;
import com.imperium.astroguide.model.dto.response.IngestResponse;
import com.imperium.astroguide.service.IngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 解析 → 分块 → 写入 VectorStore。
 */
@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

    private final DocumentParserService documentParserService;
    private final TextChunker textChunker;
    @Nullable
    private final org.springframework.ai.vectorstore.VectorStore vectorStore;

    @Autowired(required = false)
    public IngestServiceImpl(
            DocumentParserService documentParserService,
            TextChunker textChunker,
            @Nullable org.springframework.ai.vectorstore.VectorStore vectorStore) {
        this.documentParserService = documentParserService;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
    }

    @Override
    public IngestResponse ingestFromFile(MultipartFile file, String sourceNameOverride) {
        if (file == null || file.isEmpty()) {
            return IngestResponse.builder()
                    .accepted(false)
                    .source(null)
                    .chunksAdded(0)
                    .message("No file provided")
                    .build();
        }
        try (InputStream is = file.getInputStream()) {
            return ingestFromStream(
                    is,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    sourceNameOverride);
        } catch (Exception e) {
            log.warn("Ingest file failed: {}", e.getMessage());
            return IngestResponse.builder()
                    .accepted(false)
                    .source(file.getOriginalFilename())
                    .chunksAdded(0)
                    .message("Failed to read file: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public IngestResponse ingestFromStream(InputStream inputStream, String filename, String contentType, String sourceNameOverride) {
        if (vectorStore == null) {
            return IngestResponse.builder()
                    .accepted(false)
                    .source(filename)
                    .chunksAdded(0)
                    .message("RAG is disabled; vector store not available")
                    .build();
        }
        try {
            DocumentParseResult parsed = documentParserService.parse(inputStream, filename, contentType);
            String sourceLabel = (sourceNameOverride != null && !sourceNameOverride.isBlank())
                    ? sourceNameOverride
                    : parsed.getSourceLabel();
            return addChunksToStore(parsed.getFullText(), sourceLabel);
        } catch (IllegalArgumentException e) {
            return IngestResponse.builder()
                    .accepted(false)
                    .source(filename)
                    .chunksAdded(0)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.warn("Ingest stream failed: {}", e.getMessage());
            return IngestResponse.builder()
                    .accepted(false)
                    .source(filename)
                    .chunksAdded(0)
                    .message("Parse or ingest failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public IngestResponse ingestFromText(String content, String sourceName) {
        if (vectorStore == null) {
            return IngestResponse.builder()
                    .accepted(false)
                    .source(sourceName)
                    .chunksAdded(0)
                    .message("RAG is disabled; vector store not available")
                    .build();
        }
        if (content == null || content.isBlank()) {
            return IngestResponse.builder()
                    .accepted(false)
                    .source(sourceName)
                    .chunksAdded(0)
                    .message("Content is empty")
                    .build();
        }
        String label = (sourceName != null && !sourceName.isBlank()) ? sourceName : "text_" + UUID.randomUUID().toString().substring(0, 8);
        return addChunksToStore(content.trim(), label);
    }

    private IngestResponse addChunksToStore(String fullText, String sourceLabel) {
        List<String> chunks = textChunker.chunk(fullText);
        if (chunks.isEmpty()) {
            return IngestResponse.builder()
                    .accepted(true)
                    .source(sourceLabel)
                    .chunksAdded(0)
                    .message("No text chunks produced (empty or too short)")
                    .build();
        }
        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of(
                    "source", sourceLabel,
                    "chunk_index", i,
                    "chunk_total", chunks.size());
            documents.add(new Document(chunks.get(i), metadata));
        }
        try {
            vectorStore.add(documents);
            log.info("Ingest completed: source={}, chunks={}", sourceLabel, chunks.size());
            return IngestResponse.builder()
                    .accepted(true)
                    .source(sourceLabel)
                    .chunksAdded(chunks.size())
                    .message("Ingested " + chunks.size() + " chunks into vector store")
                    .build();
        } catch (Exception e) {
            log.warn("VectorStore.add failed: {}", e.getMessage());
            return IngestResponse.builder()
                    .accepted(false)
                    .source(sourceLabel)
                    .chunksAdded(0)
                    .message("Failed to write to vector store: " + e.getMessage())
                    .build();
        }
    }
}
