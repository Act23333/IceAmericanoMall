package org.icedamericanomall.ai.domain.model.product;

/**
 * 商品命中项（领域 DTO）—— 关键词召回与向量召回的统一结果形状。
 *
 * @param productId   商品业务ID
 * @param name        商品名称
 * @param description 商品描述（重排序用，可为 null）
 * @param priceCents  价格（分）
 * @param soldCount   销量
 */
public record ProductHit(
        String productId,
        String name,
        String description,
        Integer priceCents,
        Integer soldCount
) {}
