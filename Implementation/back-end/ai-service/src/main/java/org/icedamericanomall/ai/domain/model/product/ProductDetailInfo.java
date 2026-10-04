package org.icedamericanomall.ai.domain.model.product;

/**
 * 商品详情（领域 DTO）—— Agent 工具消费的商品事实。
 *
 * @param productId   商品业务ID
 * @param name        商品名称
 * @param brand       品牌
 * @param description 描述
 * @param soldCount   销量
 * @param attributes  属性参数（键值对文本）
 */
public record ProductDetailInfo(
        String productId,
        String name,
        String brand,
        String description,
        Integer soldCount,
        String attributes
) {}
