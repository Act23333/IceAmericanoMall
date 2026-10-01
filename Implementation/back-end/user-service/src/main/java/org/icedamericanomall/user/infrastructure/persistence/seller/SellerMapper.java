package org.icedamericanomall.user.infrastructure.persistence.seller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.infrastructure.persistence.seller.SellerDO;


@Mapper
public interface SellerMapper extends BaseMapper<SellerDO> {
}
