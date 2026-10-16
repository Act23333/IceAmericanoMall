package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 商品转换器。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为构建失败
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）—— 下列 {@code ignore} 均为
 * 「有意不映射」的显式声明。
 */
@Mapper(componentModel = "spring")
public interface SpuConverter {

    /**
     * 聚合 → 商品 VO。
     *
     * <p>三个字段由编排层按用例回填，不在此处映射（同一 VO 同时服务列表页与详情页，
     * 各页填充策略不同）：
     * <ul>
     *   <li>{@code skus} —— 仅详情页填充（{@code SpuManager} 经 {@code SkuConverter} 映射，列表页恒空）；</li>
     *   <li>{@code minPrice} —— 由 {@code SpuManager} 按 SKU 集合取最小售价（列表页批量回填）；</li>
     *   <li>{@code promotions} —— 展示型字段，由 {@code SpuPromotionManager} fail-open 回填
     *       （marketing 不可用 → 空列表，ADR-057）。</li>
     * </ul>
     */
    @Mapping(target = "skus", ignore = true)
    @Mapping(target = "minPrice", ignore = true)
    @Mapping(target = "promotions", ignore = true)
    SpuVO toVO(Spu spu);
}
