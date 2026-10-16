package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.domain.model.sku.Sku;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * SKU 转换器。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为构建失败
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）—— 下列 {@code ignore} 均为
 * 「有意不映射」的显式声明。
 */
@Mapper(componentModel = "spring")
public interface SkuConverter {

    SkuVO toVO(Sku sku);

    List<SkuVO> toVOs(List<Sku> skus);

    /**
     * 聚合 → I13 内部 CO。
     *
     * <p>四个展示/计价字段（{@code seller_id} / {@code product_name} / {@code category_id} /
     * {@code freight_template_id}）在 {@code sku} 表<b>没有列</b>，只能来自 {@code product} 表：
     * 由 {@code SkuManager} 经 ADR-057 读模型端口 {@code SkuProductDisplayQuery}
     * （{@code sku LEFT JOIN product}）批量回填，商品行缺失即保持 {@code null}（fail-open，
     * 不得把展示字段缺失放大成「SKU 不存在」）。故此处显式 ignore 而非在别处再映射。
     */
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "freightTemplateId", ignore = true)
    SkuCO toCO(Sku sku);

    List<SkuCO> toCOs(List<Sku> skus);
}
