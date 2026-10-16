package org.icedamericanomall.search.adapter.request;

import org.icedamericanomall.client.KnowledgeVectorClient;

import java.util.List;

/**
 * 内部向量检索入参（{@code POST /internal/search/vector}）。
 *
 * <p>请求体为<b>向后兼容的超集</b>：{@code {"embedding": [Double], "size": int?}} 是历史形状
 * （仅按商品索引检索），V5.4 起新增两个可选维度：
 * <ul>
 *   <li>{@code sellerId} —— 商家过滤（null = 全站）；</li>
 *   <li>{@code index} —— 目标索引（{@code spus} 缺省 / {@code knowledge_vectors}）。</li>
 * </ul>
 * 旧调用方（不传新字段）行为完全不变。
 *
 * @param embedding 查询向量（维度取决于 Embedding 模型）
 * @param size      Top-K 数量，缺省或非法（≤0）时归一为 10
 * @param sellerId  商家过滤（可选）
 * @param index     目标索引（可选，缺省走商品索引）
 */
public record VectorSearchReq(List<Double> embedding, int size, Long sellerId, String index) {

    private static final int DEFAULT_SIZE = 10;

    /** 归一化 Top-K：非法值回退默认值。 */
    @Override
    public int size() {
        return size > 0 ? size : DEFAULT_SIZE;
    }

    /** 归一化索引名：空白值回退商品索引（契约常量取自 ia-api，避免魔法值）。 */
    public String normalizedIndex() {
        return index == null || index.isBlank() ? KnowledgeVectorClient.INDEX_PRODUCTS : index.trim();
    }
}
