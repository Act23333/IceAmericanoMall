package org.icedamericanomall.search.domain.service;

import lombok.extern.slf4j.Slf4j;


import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档分块引擎 —— 按文档类型定制切分策略（自 ai-service {@code rag.DocumentChunker} 迁入）。
 *
 * <p><b>归属决策（V5.4）</b>：分块与向量化都留在 search-service（向量索引所有方），
 * ai-service 只提交原文并取回 chunk 文本。因此本引擎<b>只有一个家</b>——ai-service 的
 * 同名旧类已删除，不存在两处并行实现。
 *
 * <p>大厂对标: DoorDash 分层索引策略（按内容类型定制 chunk 策略）。
 *
 * <p>切分结果直接对应 {@code knowledge_vectors} 索引中「每 chunk 一条 doc」的写入单元
 * （ES 7.17 dense_vector 不支持数组多向量字段，故一 chunk 一 doc 是定案）。
 */


public class DocumentChunker {

    /** FAQ 问答标记（Q1:/Q:/问:）。 */
    private static final Pattern QA_SPLITTER = Pattern.compile("Q\\d*[：:]|问[：:]");

    /** 段落边界（空行或句末标点后的空白）。 */
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("\\n\\s*\\n|(?<=[。！？])\\s*");

    /** 文档类型。 */
    public enum DocType { FAQ, POLICY, PRODUCT_MANUAL, ANNOUNCEMENT }

    /**
     * 自动识别文档类型后分块 —— 知识库条目没有显式 docType 字段，按内容特征选择策略：
     * 命中 FAQ 标记（{@code Q1:}/{@code 问:}）走问答切分，否则按政策类段落切分。
     *
     * @param title   条目标题
     * @param content 条目正文
     * @return 分块结果（保证非空，至少一块）
     */
    public List<Chunk> chunkAuto(String title, String content) {
        if (content == null || content.isBlank()) {
            return List.of(new Chunk(title, content == null ? "" : content));
        }
        DocType type = QA_SPLITTER.matcher(content).find() ? DocType.FAQ : DocType.POLICY;
        return chunk(type, title, content);
    }

    /**
     * 按文档类型分块。
     *
     * @param docType 文档类型
     * @param title   文档标题
     * @param content 文档内容
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
     * FAQ 按 Q&A 对分块（100-300 token per chunk）。
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
            if (trimmed.isEmpty()) {
                continue;
            }
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
     *
     * @param title chunk 标题
     * @param text  chunk 正文
     */
    public record Chunk(String title, String text) {
    }
}
