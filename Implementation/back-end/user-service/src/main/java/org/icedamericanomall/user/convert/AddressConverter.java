package org.icedamericanomall.user.convert;

import org.icedamericanomall.user.domain.entity.AddressEntity;
import org.icedamericanomall.user.domain.vo.AddressResp;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AddressConverter {

    AddressResp map(AddressEntity entity);

    List<AddressResp> mapList(List<AddressEntity> list);
}
