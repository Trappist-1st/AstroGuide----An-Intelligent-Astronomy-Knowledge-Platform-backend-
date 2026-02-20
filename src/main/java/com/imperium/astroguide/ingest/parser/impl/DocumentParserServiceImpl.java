package com.imperium.astroguide.ingest.parser.impl;

import com.imperium.astroguide.ingest.parser.DocumentParseResult;
import com.imperium.astroguide.ingest.parser.DocumentParserService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 支持 PDF、EPUB、TXT、MD 的解析实现。
 */
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserServiceImpl.class);

    private static final String PDF = "application/pdf";
    private static final String EPUB = "application/epub+zip";
    private static final String TEXT = "text/plain";
    private static final String MARKDOWN = "text/markdown";

    @Override
    public DocumentParseResult parse(InputStream inputStream, String filename, String contentType) {
        String type = resolveMediaType(filename, contentType);
        return switch (type) {
            case PDF -> parsePdf(inputStream, filename);
            case EPUB -> parseEpub(inputStream, filename);
            case TEXT, MARKDOWN -> parseText(inputStream, filename);
            default -> throw new IllegalArgumentException("Unsupported format: " + type + " (filename=" + filename + ")");
        };
    }

    private String resolveMediaType(String filename, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String lower = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (lower.equals(PDF) || lower.equals(EPUB) || lower.equals(TEXT) || lower.equals(MARKDOWN)) {
                return lower;
            }
        }
        if (filename != null && !filename.isBlank()) {
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
            return switch (ext) {
                case "pdf" -> PDF;
                case "epub" -> EPUB;
                case "txt" -> TEXT;
                case "md", "markdown" -> MARKDOWN;
                default -> throw new IllegalArgumentException("Unsupported file extension: ." + ext);
            };
        }
        throw new IllegalArgumentException("Cannot determine format: provide filename or content-type");
    }

    private DocumentParseResult parsePdf(InputStream inputStream, String filename) {
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            String sourceLabel = filename != null && !filename.isBlank() ? filename : "document.pdf";
            return DocumentParseResult.builder()
                    .fullText(normalizeText(text))
                    .sourceLabel(sourceLabel)
                    .build();
        } catch (IOException e) {
            log.warn("PDF parse failed: {}", e.getMessage());
            throw new RuntimeException("Failed to parse PDF", e);
        }
    }

    private DocumentParseResult parseEpub(InputStream inputStream, String filename) {
        try {
            EpubReader reader = new EpubReader();
            Book book = reader.readEpub(inputStream);
            String title = book.getTitle();
            String sourceLabel = (title != null && !title.isBlank()) ? title : (filename != null && !filename.isBlank() ? filename : "document.epub");
            StringBuilder full = new StringBuilder();
            List<Resource> contents = book.getContents();
            for (Resource res : contents) {
                String href = res.getHref();
                if (href == null) continue;
                String lower = href.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".html") && !lower.endsWith(".xhtml") && !lower.endsWith(".htm")) {
                    continue;
                }
                try {
                    byte[] data = res.getData();
                    if (data == null || data.length == 0) continue;
                    String html = new String(data, StandardCharsets.UTF_8);
                    String plain = Jsoup.parse(html).text();
                    if (plain != null && !plain.isBlank()) {
                        if (full.length() > 0) full.append("\n\n");
                        full.append(plain);
                    }
                } catch (Exception e) {
                    log.debug("Skip resource {}: {}", href, e.getMessage());
                }
            }
            return DocumentParseResult.builder()
                    .fullText(normalizeText(full.toString()))
                    .sourceLabel(sourceLabel)
                    .build();
        } catch (IOException e) {
            log.warn("EPUB parse failed: {}", e.getMessage());
            throw new RuntimeException("Failed to parse EPUB", e);
        }
    }

    private DocumentParseResult parseText(InputStream inputStream, String filename) {
        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String sourceLabel = filename != null && !filename.isBlank() ? filename : "document.txt";
            return DocumentParseResult.builder()
                    .fullText(normalizeText(text))
                    .sourceLabel(sourceLabel)
                    .build();
        } catch (IOException e) {
            log.warn("Text parse failed: {}", e.getMessage());
            throw new RuntimeException("Failed to read text", e);
        }
    }

    private static String normalizeText(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n").trim();
    }
}
