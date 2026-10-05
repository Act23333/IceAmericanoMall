package org.icedamericanomall.search.domain.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记向量化文本拼接（领域服务，纯函数）—— I50 单向量摄取的单一事实源。
 *
 * <p>拼接口径（契约 I50 / ADR-046 管道第二次复用）：{@code title + content}，
 * 与商品侧 {@code ProductVectorTextBuilder} 同风格——各段 trim、非空白才拼接、
 * 分隔符为单个空格；笔记短文本单向量语义已足够，不分块。
 */
public final class NoteVectorTextBuilder {

    /** 拼接分隔符（单个空格）。 */
    private static final String SEPARATOR = " ";

    private NoteVectorTextBuilder() {
    }

    /**
     * 拼接向量化文本。
     *
     * @param title   笔记标题（主来源，null/空白则跳过）
     * @param content 正文（可空/空白则跳过）
     * @return 拼接后的向量化文本（各段均空白时为空串，无悬挂分隔空格）
     */
    public static String build(String title, String content) {
        List<String> parts = new ArrayList<>(2);
        appendIfNotBlank(parts, title);
        appendIfNotBlank(parts, content);
        return String.join(SEPARATOR, parts);
    }

    private static void appendIfNotBlank(List<String> parts, String part) {
        if (part != null && !part.isBlank()) {
            parts.add(part.trim());
        }
    }
}
