package org.icedamericanomall.merchant.infrastructure.persistence.seller;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.merchant.infrastructure.persistence.seller.MerchantPO;


@Mapper
public interface MerchantMapper extends BaseMapper<MerchantPO> {
}
