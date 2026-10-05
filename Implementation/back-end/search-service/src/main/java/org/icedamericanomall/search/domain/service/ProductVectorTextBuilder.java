package org.icedamericanomall.search.domain.service;

/**
 * 商品向量化文本拼接（领域服务，纯函数）—— I47 单向量摄取与 reindex 全量重建共用的单一事实源。
 *
 * <p>拼接口径（ADR-046 §2）：{@code name + description}，可选拼 {@code brand}
 * （非空白才拼接，分隔符为单个空格）；商品短文本单向量语义已足够，不分块。
 */
public final class ProductVectorTextBuilder {

    /** 拼接分隔符（单个空格）。 */
    private static final String SEPARATOR = " ";

    private ProductVectorTextBuilder() {
    }

    /**
     * 拼接向量化文本。
     *
     * @param name        商品名（主来源，null 视为空）
     * @param description 描述（可空/空白则跳过）
     * @param brand       品牌（可空/空白则跳过）
     * @return 拼接后的向量化文本（各段均空白时为空串）
     */
    public static String build(String name, String description, String brand) {
        StringBuilder text = new StringBuilder(name == null ? "" : name.trim());
        appendIfNotBlank(text, description);
        appendIfNotBlank(text, brand);
        return text.toString();
    }

    private static void appendIfNotBlank(StringBuilder text, String part) {
        if (part != null && !part.isBlank()) {
            text.append(SEPARATOR).append(part.trim());
        }
    }
}
