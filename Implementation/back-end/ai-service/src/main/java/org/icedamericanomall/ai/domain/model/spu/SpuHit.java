package org.icedamericanomall.ai.domain.model.spu;

/**
 * 商品命中项（领域 DTO）—— 关键词召回与向量召回的统一结果形状。
 *
 * @param spuId   商品业务ID
 * @param name        商品名称
 * @param description 商品描述（重排序用，可为 null）
 * @param priceCents  价格（分）
 * @param soldCount   销量
 */
public record SpuHit(
        String spuId,
        String name,
        String description,
        Integer priceCents,
        Integer soldCount
) {}
