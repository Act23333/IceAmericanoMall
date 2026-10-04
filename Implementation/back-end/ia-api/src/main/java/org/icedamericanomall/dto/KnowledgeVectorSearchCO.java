package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 向量检索请求（跨服务契约对象，ai-service → search-service）。
 *
 * <p>对应内部端点 {@code POST /internal/search/vector}。该端点自 V5.4 起支持
 * {@code index} 与 {@code sellerId} 两个可选维度：
 * <ul>
 *   <li>{@code index} 缺省（或 {@code products}）→ 商品 kNN 检索（历史行为，报文形状不变）；</li>
 *   <li>{@code index = knowledge_vectors} → 商家知识库 chunk 检索（按 {@code sellerId} 过滤）。</li>
 * </ul>
 * 请求体字段数组是<b>向后兼容的超集</b>：旧调用方只发 {@code embedding/size}，服务端按缺省语义处理。
 */
@Data
public class KnowledgeVectorSearchCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 查询向量（维度必须与该索引的 dense_vector dims 一致）。 */
    private List<Double> embedding;

    /** Top-K，缺省或非法（≤0）时服务端归一为默认值。 */
    private Integer size;

    /** 商家过滤（非空时按 sellerId 过滤；为空表示全站）。 */
    private Long sellerId;

    /** 目标索引；缺省走商品索引。 */
    private String index;
}
