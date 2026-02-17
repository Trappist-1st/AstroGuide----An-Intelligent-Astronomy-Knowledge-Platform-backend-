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

    public static void load() {
        Path envPath = Paths.get(System.getProperty("user.dir")).resolve(".env");
        if (!Files.isRegularFile(envPath)) {
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
                    if (!key.isEmpty()) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DotenvLoader] Failed to load .env: " + e.getMessage());
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s;
    }
}
