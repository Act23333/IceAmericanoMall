package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserInfoResp map(UserEntity entity);

    List<UserInfoResp> mapList(List<UserEntity> list);

    default Integer map(UserStatusEnum value) {
        return value == null ? null : value.getStatus();
    }
}
