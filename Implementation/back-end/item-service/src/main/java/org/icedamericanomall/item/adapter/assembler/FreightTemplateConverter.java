package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.domain.model.freight.FreightTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 运费模板转换器。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为构建失败
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）—— 下列 {@code ignore} 均为
 * 「有意不映射」的显式声明。
 */
@Mapper(componentModel = "spring")
public interface FreightTemplateConverter {

    FreightTemplateVO toVO(FreightTemplate template);

    List<FreightTemplateVO> toVOs(List<FreightTemplate> templates);

    /**
     * 聚合 → 内部 CO（I43 计价口径）。
     *
     * <p>{@code isDefault} 不是 {@code freight_template} 表的列，而是「最早创建的启用模板」
     * 这一计算事实：由 {@code FreightTemplateManager.queryEnabled} 在映射后按
     * {@code findDefaultEnabled(sellerId)} 逐行比对后回填，故不在此处映射。
     */
    @Mapping(target = "isDefault", ignore = true)
    FreightTemplateCO toCO(FreightTemplate template);

    List<FreightTemplateCO> toCOs(List<FreightTemplate> templates);
}
