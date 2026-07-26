package org.icedamericanomall.dto;

import java.io.Serializable;

/** 商品搜索结果 — Java Record。映射 search-service ProductSearchVO。 */
public record ProductSearchResult(
        Long id,
        String productId,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImage,
        Integer price,
        Integer soldCount
) implements Serializable {}
