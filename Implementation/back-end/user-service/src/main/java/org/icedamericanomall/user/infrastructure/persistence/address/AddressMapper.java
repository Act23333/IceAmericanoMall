package org.icedamericanomall.user.infrastructure.persistence.address;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.infrastructure.persistence.address.AddressDO;


@Mapper
public interface AddressMapper extends BaseMapper<AddressDO> {

}
