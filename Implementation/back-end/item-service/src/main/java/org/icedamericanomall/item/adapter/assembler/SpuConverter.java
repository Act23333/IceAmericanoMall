package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.dto.SpuDetailCO;
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
     *
     * <p><b>为何 {@code name → spuName} 要显式写</b>：域聚合字段仍是 {@link Spu#getName()}
     * （库列 {@code spu.name}，本批不动），而线格式键按契约 §1.8④ 收口为 {@code spu_name} ——
     * 两侧名字<b>有意不同</b>，这层映射必须显式声明，不能依赖「同名自动匹配」的巧合。
     */
    @Mapping(source = "name", target = "spuName")
    @Mapping(target = "skus", ignore = true)
    @Mapping(target = "minPrice", ignore = true)
    @Mapping(target = "promotions", ignore = true)
    SpuVO toVO(Spu spu);

    /**
     * P2 装配结果 → 内部契约 {@code SpuDetailCO}（I70 {@code GET /internal/item/spu/{spuId}}）。
     *
     * <p><b>纯投影，无业务逻辑</b>：入参恒为 {@code SpuManager#getSpuDetail(String)} 的产出
     * （同一装配源），本方法只裁剪字段面，不做任何取数 —— 一旦它开始查库/取远程，
     * 就变成了契约明令禁止的「第二套装配」。
     *
     * <p>{@code images} / {@code attributes} 显式 ignore：它们是 {@code spu} 表的历史保留列，
     * <b>不在 P2 装配的字段面内</b>（{@code SpuVO} 无对应字段）；迁移前经 Feign 读公开 P2
     * 解码成 {@code SpuDetailCO} 时这两个字段同样恒为 {@code null}，故本投影保持逐字段一致，
     * 不在此处新增一次库读（那会让内部出口的数据面与 P2 分叉）。
     */
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "attributes", ignore = true)
    SpuDetailCO toDetailCO(SpuVO vo);
}
