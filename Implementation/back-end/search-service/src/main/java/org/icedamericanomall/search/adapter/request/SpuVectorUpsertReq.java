package org.icedamericanomall.search.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 商品向量摄取入参（内部端点 {@code POST /internal/search/vector/product/upsert}，契约 I47）。
 *
 * <p>字段与 ia-api {@code SpuVectorUpsertCO} 一一对齐：只收商品原文与展示字段，
 * 分块/向量在 search-service 内完成（单向量不分块，ADR-046），调用方不传向量。
 *
 * @param spuId   商品 id（幂等键）
 * @param name        商品名（向量化文本主来源）
 * @param description 商品描述（向量化文本）
 * @param brand       品牌（可选拼入向量化文本）
 * @param categoryId  分类ID
 * @param mainImage   主图
 * @param price       展示价（分）
 * @param soldCount   销量
 */
public record SpuVectorUpsertReq(
        @NotNull(message = "spuId 不能为空") Long spuId,
        @NotBlank(message = "name 不能为空") String name,
        String description,
        String brand,
        Long categoryId,
        String mainImage,
        Integer price,
        Integer soldCount
) implements Serializable {
}
