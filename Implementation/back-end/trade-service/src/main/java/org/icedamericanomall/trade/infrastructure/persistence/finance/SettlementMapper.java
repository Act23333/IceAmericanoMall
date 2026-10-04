package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.trade.infrastructure.persistence.finance.SettlementPO;

@Mapper
public interface SettlementMapper extends BaseMapper<SettlementPO> {
}
