package org.icedamericanomall.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * V3.0.3: 文档分块引擎 — 按文档类型定制切分策略。
 * <p>
 * 用于商家知识库管理：FAQ/政策/产品手册/公告 → 结构化分块 → Embedding → ES索引。
 * <p>
 * 大厂对标: DoorDash 分层索引策略 (按内容类型定制chunk策略)
 */
@Slf4j
@Component
public class DocumentChunker {

    private static final Pattern QA_SPLITTER = Pattern.compile("Q\\d*[：:]|问[：:]");
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("\\n\\s*\\n|(?<=[。！？])\\s*");
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("(?<=[。！？；])");

    public enum DocType { FAQ, POLICY, PRODUCT_MANUAL, ANNOUNCEMENT }

    /**
     * @param docType  文档类型
     * @param title    文档标题
     * @param content  文档内容
     * @return 分块结果列表
     */
    public List<Chunk> chunk(DocType docType, String title, String content) {
        return switch (docType) {
            case FAQ -> chunkFAQ(title, content);
            case POLICY -> chunkByParagraph(title, content, 300, 500);
            case PRODUCT_MANUAL -> chunkByParagraph(title, content, 500, 1000);
            case ANNOUNCEMENT -> chunkByParagraph(title, content, 300, 500);
        };
    }

    /**
     * FAQ 按 Q&A 对分块 (100-300 token per chunk, overlap 50)。
     */
    private List<Chunk> chunkFAQ(String title, String content) {
        List<Chunk> chunks = new ArrayList<>();
        String[] parts = QA_SPLITTER.split(content);
        // 第一个 split 为空（在第一个标记之前），跳过
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i].trim();
            if (chunk.length() > 10) { // 过滤太短的片段
                chunks.add(new Chunk(title + " - Q" + i, chunk));
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(new Chunk(title, content.substring(0, Math.min(content.length(), 500))));
        }
        return chunks;
    }

    /**
     * 按段落分块，限制每个 chunk 的最大长度。
     */
    private List<Chunk> chunkByParagraph(String title, String content, int minLen, int maxLen) {
        List<Chunk> chunks = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_SPLITTER.split(content);
        StringBuilder current = new StringBuilder();
        int chunkIdx = 1;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (current.length() + trimmed.length() > maxLen && current.length() > minLen) {
                chunks.add(new Chunk(title + " - Part " + chunkIdx++, current.toString().trim()));
                current = new StringBuilder();
            }
            current.append(trimmed).append("\n");
        }
        if (current.length() > 0) {
            chunks.add(new Chunk(title + " - Part " + chunkIdx, current.toString().trim()));
        }
        if (chunks.isEmpty() && !content.isEmpty()) {
            chunks.add(new Chunk(title, content.substring(0, Math.min(content.length(), maxLen))));
        }
        return chunks;
    }

    /**
     * 文档分块结果。
     */
    public record Chunk(String title, String text) {}
}
