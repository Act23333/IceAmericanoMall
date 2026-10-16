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
 *
 * <p><b>同名异义必须显式 ignore（ADR-058 §4）</b>：{@code Sku.spuId} 是<b>技术外键</b>
 * （{@code sku.spu_id BIGINT}），而 {@code SkuVO.spuId}/{@code SkuCO.spuId} 是<b>业务键</b>
 * 32-hex —— 二者同名异义，靠自动转换会把 {@code Long 5} 静默写成 {@code "5"}（看似有值、
 * 最难排查），故必须 ignore 并由 {@code SkuManager} 经 ADR-057 读模型回填。
 */
@Mapper(componentModel = "spring")
public interface SkuConverter {

    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "spuName", ignore = true)
    SkuVO toVO(Sku sku);

    List<SkuVO> toVOs(List<Sku> skus);

    /**
     * 聚合 → I13 内部 CO。
     *
     * <p>{@code spu_id}（业务键）/ {@code spu_name} / {@code seller_id} / {@code category_id} /
     * {@code freight_template_id} 在 {@code sku} 表<b>没有对应语义列</b>（{@code sku.spu_id}
     * 是同名的技术外键），只能来自 {@code spu} 表：由 {@code SkuManager} 经 ADR-057 读模型端口
     * {@code SkuSpuDisplayQuery}（{@code sku LEFT JOIN spu}）批量回填，商品行缺失即保持
     * {@code null}（fail-open，不得把展示字段缺失放大成「SKU 不存在」）。故此处显式 ignore
     * 而非在别处再映射。{@code skuId}（业务键）同理——技术键 {@code Sku.id} 不得自动落进去。
     */
    @Mapping(target = "skuId", ignore = true)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "spuName", ignore = true)
    @Mapping(target = "sellerId", ignore = true)
    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "freightTemplateId", ignore = true)
    SkuCO toCO(Sku sku);

    List<SkuCO> toCOs(List<Sku> skus);
}
