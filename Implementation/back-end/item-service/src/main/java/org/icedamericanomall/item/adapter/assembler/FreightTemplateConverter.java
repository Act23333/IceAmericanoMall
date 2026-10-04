package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.domain.model.freight.FreightTemplate;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FreightTemplateConverter {

    FreightTemplateVO toVO(FreightTemplate template);

    List<FreightTemplateVO> toVOs(List<FreightTemplate> templates);

    FreightTemplateCO toCO(FreightTemplate template);

    List<FreightTemplateCO> toCOs(List<FreightTemplate> templates);
}