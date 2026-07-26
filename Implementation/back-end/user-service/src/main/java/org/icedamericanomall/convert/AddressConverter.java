package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.AddressEntity;
import org.icedamericanomall.domain.vo.AddressResp;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AddressConverter {

    AddressResp map(AddressEntity entity);

    List<AddressResp> mapList(List<AddressEntity> list);
}
