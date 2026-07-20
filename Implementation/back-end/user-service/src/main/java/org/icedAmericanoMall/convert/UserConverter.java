package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
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
