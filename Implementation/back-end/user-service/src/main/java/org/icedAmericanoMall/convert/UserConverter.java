package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;

import org.icedAmericanoMall.mapper.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;


/**
 * @ClassName: UserMapper
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/28 23:30
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.convert
 */
@Mapper
public interface UserConverter{
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    // 单个对象
    UserInfoResp map(UserEntity entity);
    // 集合对象
    List<UserInfoResp> mapList(List<UserEntity> list);

    // MapStruct 无法自动推断 UserStatusEnum -> Integer 的映射
    default Integer map(UserStatusEnum value) {
        return value == null ? null : value.getStatus();
    }
}
