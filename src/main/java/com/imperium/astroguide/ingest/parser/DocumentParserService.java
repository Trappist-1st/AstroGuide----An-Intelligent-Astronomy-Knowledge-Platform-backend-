package com.imperium.astroguide.ingest.parser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 将各类资料（PDF、EPUB、TXT、MD）解析为纯文本。
 */
public interface DocumentParserService {

    /**
     * 根据文件名与内容类型解析输入流，得到纯文本与来源标签。
     *
     * @param inputStream 文件流（调用方不关闭）
     * @param filename    原始文件名（用于选择解析器与默认 sourceLabel）
     * @param contentType 可选，如 application/pdf、application/epub+zip、text/plain
     * @return 解析结果，若格式不支持或解析失败可返回 null 或抛异常
     */
    DocumentParseResult parse(InputStream inputStream, String filename, String contentType);
}
