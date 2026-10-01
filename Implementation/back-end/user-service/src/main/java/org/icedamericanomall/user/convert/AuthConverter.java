package org.icedamericanomall.user.convert;

import org.icedamericanomall.dto.WechatMiniLoginReqCO;
import org.icedamericanomall.integration.auth.model.WechatMiniLoginReq;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthConverter {
    WechatMiniLoginReq map(WechatMiniLoginReqCO wechatMiniLoginReqDTO);
}
