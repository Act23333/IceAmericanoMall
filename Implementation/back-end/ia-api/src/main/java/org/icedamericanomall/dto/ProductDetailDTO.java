package org.icedamericanomall.dto;

import java.io.Serializable;

/** 商品详情 — Java Record。映射 item-service ProductVO。 */
public record ProductDetailDTO(
        Long id,
        String productId,
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
