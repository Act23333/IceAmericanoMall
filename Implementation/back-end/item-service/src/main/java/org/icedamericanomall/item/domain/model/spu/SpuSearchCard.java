package org.icedamericanomall.item.domain.model.spu;

/**
 * 商品检索卡片（领域读模型，ADR-052）—— 内部检索出口与 ES 重建数据源的统一数据形状。
 *
 * <p>{@code spuId} 为业务标识 32 位 hex（BINARY(16) ↔ hex 转换在持久化边界完成，ADR-050）；
 * {@code minPrice} 为在售 SKU 最低价（分；无在售 SKU 时为 0，与历史检索口径一致）；
 * {@code publishTime} 为 epoch millis（SE1 newest 排序回填用，可空）。
 */
public record SpuSearchCard(Long id, String spuId, Long categoryId, String name, String description,
                                String brand, String mainImage, Integer minPrice, Integer soldCount,
                                Long publishTime) {
}
