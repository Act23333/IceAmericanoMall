package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.request.HomeConfigReq;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.domain.model.home.HomeConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HomeConfigConverter {

    /** 请求 → 聚合: id/createTime/updateTime 由持久层填充，不来自请求体。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    HomeConfig toDomain(HomeConfigReq req);

    HomeConfigVO toVO(HomeConfig config);

    List<HomeConfigVO> toVOs(List<HomeConfig> configs);
}