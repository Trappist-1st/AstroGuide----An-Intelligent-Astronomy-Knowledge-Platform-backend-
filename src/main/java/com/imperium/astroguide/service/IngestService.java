package com.imperium.astroguide.service;

import com.imperium.astroguide.model.dto.response.IngestResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 资料摄入：解析文件/文本 → 分块 → 写入向量库。
 */
public interface IngestService {

    /**
     * 上传文件并摄入（PDF、EPUB、TXT、MD）。
     *
     * @param file           上传的文件
     * @param sourceNameOverride 可选，覆盖解析出的来源名（如自定义书名）
     * @return 摄入结果
     */
    IngestResponse ingestFromFile(MultipartFile file, String sourceNameOverride);

    /**
     * 从输入流摄入（供内部或脚本调用）。
     *
     * @param inputStream 文件流
     * @param filename    文件名（用于判断格式）
     * @param contentType 可选，如 application/pdf
     * @param sourceNameOverride 可选，覆盖来源名
     * @return 摄入结果
     */
    IngestResponse ingestFromStream(InputStream inputStream, String filename, String contentType, String sourceNameOverride);

    /**
     * 直接摄入一段文本（如粘贴或 API 传入）。
     *
     * @param content   正文
     * @param sourceName 来源显示名
     * @return 摄入结果
     */
    IngestResponse ingestFromText(String content, String sourceName);
}
