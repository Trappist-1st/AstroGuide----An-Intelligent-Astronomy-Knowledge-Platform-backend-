package com.imperium.astroguide.controller;

import com.imperium.astroguide.model.dto.request.IngestTextRequest;
import com.imperium.astroguide.model.dto.response.IngestResponse;
import com.imperium.astroguide.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资料摄入接口：上传 PDF/EPUB/TXT/MD 或提交文本，写入 Qdrant 向量库供 RAG 检索。
 */
@RestController
@RequestMapping("/api/v0/ingest")
@Tag(name = "Ingest", description = "资料摄入（写入向量库）")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 上传文件并摄入（PDF、EPUB、TXT、MD）。
     * 根据文件名或 Content-Type 自动识别格式。
     */
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件摄入", description = "支持 PDF、EPUB、TXT、MD；解析后分块并写入向量库")
    public ResponseEntity<IngestResponse> ingestFile(
            @Parameter(description = "文件（PDF/EPUB/TXT/MD）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "可选，覆盖来源显示名（如书名）")
            @RequestParam(value = "sourceName", required = false)
            @Schema(example = "《基础天文学》") String sourceName) {
        IngestResponse response = ingestService.ingestFromFile(file, sourceName);
        return ResponseEntity.ok(response);
    }

    /**
     * 直接提交一段文本并摄入。
     */
    @PostMapping(value = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "提交文本摄入", description = "将一段文本分块并写入向量库")
    public ResponseEntity<IngestResponse> ingestText(@Valid @RequestBody IngestTextRequest request) {
        IngestResponse response = ingestService.ingestFromText(
                request.getContent(),
                request.getSourceName());
        return ResponseEntity.ok(response);
    }
}
