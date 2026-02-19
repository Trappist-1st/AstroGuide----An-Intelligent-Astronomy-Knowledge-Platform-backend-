package com.imperium.astroguide.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 启动前加载项目根目录下的 .env 文件，将 KEY=VALUE 写入 System.setProperty，
 * 以便 application.yaml 中的 ${KEY} 能解析到 .env 里的值。
 * Spring Boot 会从系统属性中解析占位符，因此无需额外依赖。
 */
public final class DotenvLoader {

    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    private static final String OPENAI_BASE_URL_KEY = "OPENAI_BASE_URL";
    private static final String OPENAI_EMBEDDING_BASE_URL_KEY = "OPENAI_EMBEDDING_BASE_URL";

    public static void load() {
        Path envPath = Paths.get(System.getProperty("user.dir")).resolve(".env");
        if (!Files.isRegularFile(envPath)) {
            System.out.println("[DotenvLoader] .env file not found at: " + envPath);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(envPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                var matcher = ENV_LINE.matcher(trimmed);
                if (matcher.matches()) {
                    String key = matcher.group(1).trim();
                    String value = unquote(matcher.group(2).trim());

                    // Spring AI 的 OpenAI API client 会自动拼接 /v1 前缀。
                    // 如果用户在 .env 里把 base-url 配成 .../v1，会导致请求变成 .../v1/v1/... 从而 404。
                    if (OPENAI_BASE_URL_KEY.equals(key) || OPENAI_EMBEDDING_BASE_URL_KEY.equals(key)) {
                        String normalized = normalizeOpenAiBaseUrl(value);
                        if (!normalized.equals(value)) {
                            System.out.println("[DotenvLoader] Normalized " + key + " (removed trailing /v1): "
                                    + value + " -> " + normalized);
                        }
                        value = normalized;
                    }
                    if (!key.isEmpty()) {
                        System.setProperty(key, value);
                        System.out.println("[DotenvLoader] Loaded: " + key + " = " + (key.contains("KEY") ? "***" : value));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DotenvLoader] Failed to load .env: " + e.getMessage());
        }
    }

    private static String normalizeOpenAiBaseUrl(String value) {
        if (value == null) {
            return "";
        }

        String v = value.trim();
        // strip trailing slashes first
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }

        if (v.endsWith("/v1")) {
            v = v.substring(0, v.length() - 3);
        }

        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }

        return v;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }
}
