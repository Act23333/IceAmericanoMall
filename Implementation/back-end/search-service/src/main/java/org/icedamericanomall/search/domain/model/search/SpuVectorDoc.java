package org.icedamericanomall.search.domain.model.search;

/**
 * 商品向量文档（领域写模型）—— 商品向量摄取的输入形状（ADR-046，单向量不分块）。
 *
 * <p>与知识库 chunk 路线刻意不同：商品文本是短文本，单条 embedding 语义已足够，
 * 不引入 {@code DocumentChunker}。写入 ES 时整文档覆盖（spuId 幂等键），
 * 展示字段一并以入参为准，供 I28 商品检索与 I48 相似推荐共用。
 *
 * <p><b>标识口径（ADR-061 §决策 2）</b>：{@code spuId} 为 SPU <b>业务键</b>（32-hex），
 * 非技术主键；ES 文档 {@code _id} 仍用技术键（检索卡片 {@code id}），
 * 两者由 ES 文档字段共同承载，技术键解析在文档落库边界完成。
 *
 * @param spuId    SPU 业务键（32-hex，幂等键）
 * @param name        商品名（向量化文本主来源）
 * @param description 商品描述（向量化文本）
 * @param brand       品牌（可选拼入向量化文本）
 * @param categoryId  分类ID
 * @param mainImage   主图
 * @param price       展示价（分）
 * @param soldCount   销量
 * @param embedding   商品向量（以新向量为准）
 */
public record SpuVectorDoc(String spuId, String name, String description, String brand,
                               Long categoryId, String mainImage, Integer price,
                               Integer soldCount, double[] embedding) {
}
