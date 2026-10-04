package org.icedamericanomall.user.infrastructure.persistence.address;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.infrastructure.persistence.address.AddressPO;


@Mapper
public interface AddressMapper extends BaseMapper<AddressPO> {

}
