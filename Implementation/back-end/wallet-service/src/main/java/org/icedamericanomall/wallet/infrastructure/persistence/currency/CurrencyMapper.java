package org.icedamericanomall.wallet.infrastructure.persistence.currency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 币种 Mapper —— {@code currency} 表（业务唯一键 {@code uk_code}）。
 */
@Mapper
public interface CurrencyMapper extends BaseMapper<CurrencyPO> {
}
