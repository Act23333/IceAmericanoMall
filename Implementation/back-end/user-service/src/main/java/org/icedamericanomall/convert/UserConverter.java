package org.icedamericanomall.convert;

import org.icedamericanomall.constants.UserStatusEnum;
import org.icedamericanomall.domain.dto.UpdateProfileReq;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.domain.vo.UserInfoResp;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        // 1. 忽略源为 null 的属性（不覆盖目标值）
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        // 2. 忽略目标对象中找不到对应来源的属性（不报错/警告）
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserConverter {

    UserInfoResp map(UserEntity entity);

    List<UserInfoResp> mapList(List<UserEntity> list);

    default Integer map(UserStatusEnum value) {
        return value == null ? null : value.getStatus();
    }

    void updateProfile(@MappingTarget UserEntity entity, UpdateProfileReq req);
}
