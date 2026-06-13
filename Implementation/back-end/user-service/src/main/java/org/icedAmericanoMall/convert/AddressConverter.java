package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.AddressEntity;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.AddressResp;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
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
public interface AddressConverter {
    AddressConverter INSTANCE = Mappers.getMapper(AddressConverter.class);

    // 单个对象
    AddressResp map(AddressEntity entity);
    // 集合对象
    List<AddressResp> mapList(List<AddressEntity> list);
}
