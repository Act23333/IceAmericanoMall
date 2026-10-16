package org.icedamericanomall.dto;

import java.io.Serializable;

/** 商品详情 — Java Record。映射 item-service SpuVO。 */
public record SpuDetailCO(
        Long id,
        String spuId,
        String name,
        String description,
        String brand,
        Long categoryId,
        String mainImage,
        String images,
        String attributes,
        Integer soldCount,
        Integer status
) implements Serializable {}
