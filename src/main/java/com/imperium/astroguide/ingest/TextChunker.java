package com.imperium.astroguide.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将长文本按块大小与重叠切分为多段，便于向量化与检索。
 * <p>
 * 分块策略：优先按句子边界切分，保证块内每个句子完整、不拦腰截断；
 * 超长句再按标点或字符做兜底切分。
 */
@Component
public class TextChunker {

    /** 句子结束符（中英文）：句号、问号、感叹号等后的空白或换行作为切分点 */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？.!?])\\s+|\\n+");

    /** 超长句兜底切分：按逗号、分号、顿号等次要边界 */
    private static final Pattern WEAK_BOUNDARY = Pattern.compile("(?<=[，；、,;])\\s*");

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunker(
            @Value("${app.ingest.chunk-size:600}") int chunkSize,
            @Value("${app.ingest.chunk-overlap:80}") int chunkOverlap) {
        this.chunkSize = Math.max(200, Math.min(2000, chunkSize));
        this.chunkOverlap = Math.max(0, Math.min(this.chunkSize / 2, chunkOverlap));
    }

    /**
     * 按句子边界分块：每块由完整句子组成，不截断句子；超长句再按标点或长度兜底切分。
     */
    public List<String> chunk(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }
        String normalized = fullText.replace("\r\n", "\n").replace("\r", "\n").trim();
        List<String> sentences = splitIntoSentences(normalized);
        return groupSentencesIntoChunks(sentences);
    }

    /**
     * 按句子结束符和换行切分为句子（保留尾部标点在同一句内）。
     */
    private List<String> splitIntoSentences(String text) {
        List<String> list = new ArrayList<>();
        for (String s : SENTENCE_END.split(text)) {
            String t = s.replace("\n", " ").trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        // 若整段没有句末标点，会得到一整块，后面按长度兜底
        if (list.isEmpty() && !text.isBlank()) {
            list.add(text.replace("\n", " ").trim());
        }
        return list;
    }

    /**
     * 将句子列表按 chunkSize 聚合成块，块内句子完整；可选句子级重叠。
     */
    private List<String> groupSentencesIntoChunks(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        List<String> overlapBuffer = new ArrayList<>(); // 用于重叠的句子
        StringBuilder current = new StringBuilder();
        int overlapCharCount = 0;

        for (String sentence : sentences) {
            boolean singleSentenceExceedsChunk = sentence.length() > chunkSize;

            if (singleSentenceExceedsChunk) {
                // 先 flush 当前块
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    overlapBuffer.clear();
                    overlapCharCount = 0;
                }
                // 超长句：按次要标点或按长度切分
                for (String fragment : splitLongSentence(sentence)) {
                    chunks.add(fragment.trim());
                }
                continue;
            }

            int needSpace = current.length() > 0 ? 1 : 0;
            if (current.length() + needSpace + sentence.length() <= chunkSize) {
                if (current.length() > 0) current.append(" ");
                current.append(sentence);
                overlapBuffer.add(sentence);
                overlapCharCount += (needSpace + sentence.length());
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    // 重叠：从 overlapBuffer 末尾取若干句，使总字符数约等于 chunkOverlap
                    List<String> overlap = buildOverlapSentences(overlapBuffer);
                    current.setLength(0);
                    overlapCharCount = 0;
                    for (String o : overlap) {
                        if (current.length() > 0) current.append(" ");
                        current.append(o);
                        overlapCharCount += o.length() + (current.length() > 0 ? 1 : 0);
                    }
                    overlapBuffer.clear();
                    overlapBuffer.addAll(overlap);
                }
                if (current.length() + 1 + sentence.length() <= chunkSize) {
                    if (current.length() > 0) current.append(" ");
                    current.append(sentence);
                    overlapBuffer.add(sentence);
                    overlapCharCount += sentence.length() + 1;
                } else {
                    current.setLength(0);
                    current.append(sentence);
                    overlapBuffer.clear();
                    overlapBuffer.add(sentence);
                    overlapCharCount = sentence.length();
                }
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    /** 从末尾取句子，使总长度尽量接近 chunkOverlap（至少取一句） */
    private List<String> buildOverlapSentences(List<String> buffer) {
        if (buffer.isEmpty() || chunkOverlap <= 0) return List.of();
        List<String> out = new ArrayList<>();
        int len = 0;
        for (int i = buffer.size() - 1; i >= 0; i--) {
            String s = buffer.get(i);
            if (len + s.length() > chunkOverlap && !out.isEmpty()) break;
            out.add(0, s);
            len += s.length() + (out.size() > 1 ? 1 : 0);
        }
        return out;
    }

    /**
     * 超长句按次要标点（逗号、分号等）切分；若仍超长则按固定长度切（避免单块过大）。
     */
    private List<String> splitLongSentence(String sentence) {
        List<String> fragments = new ArrayList<>();
        String[] byWeak = WEAK_BOUNDARY.split(sentence);
        StringBuilder acc = new StringBuilder();
        for (String part : byWeak) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (acc.length() + p.length() + 1 <= chunkSize) {
                if (acc.length() > 0) acc.append(" ");
                acc.append(p);
            } else {
                if (acc.length() > 0) {
                    fragments.add(acc.toString().trim());
                    acc.setLength(0);
                }
                if (p.length() > chunkSize) {
                    // 仍超长：按字符切，尽量在空格处断
                    fragments.addAll(splitByLength(p));
                } else {
                    acc.append(p);
                }
            }
        }
        if (acc.length() > 0) {
            fragments.add(acc.toString().trim());
        }
        return fragments;
    }

    /** 按长度硬切，尽量在空格处断句 */
    private List<String> splitByLength(String text) {
        List<String> list = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace + 1;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) list.add(piece);
            start = end;
        }
        return list;
    }
}
