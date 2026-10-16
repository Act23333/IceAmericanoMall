package org.icedamericanomall.search.domain.model.search;

/**
 * 商品向量文档（领域写模型）—— 商品向量摄取的输入形状（ADR-046，单向量不分块）。
 *
 * <p>与知识库 chunk 路线刻意不同：商品文本是短文本，单条 embedding 语义已足够，
 * 不引入 {@code DocumentChunker}。写入 ES 时整文档覆盖（spuId 幂等键），
 * 展示字段一并以入参为准，供 I28 商品检索与 I48 相似推荐共用。
 *
 * @param spuId   商品 id（幂等键；ES 文档 _id 同源，重复摄取即覆盖）
 * @param name        商品名（向量化文本主来源）
 * @param description 商品描述（向量化文本）
 * @param brand       品牌（可选拼入向量化文本）
 * @param categoryId  分类ID
 * @param mainImage   主图
 * @param price       展示价（分）
 * @param soldCount   销量
 * @param embedding   商品向量（以新向量为准）
 */
public record SpuVectorDoc(Long spuId, String name, String description, String brand,
                               Long categoryId, String mainImage, Integer price,
                               Integer soldCount, double[] embedding) {
}
