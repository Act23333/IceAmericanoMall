package org.icedamericanomall.wallet.infrastructure.persistence.currency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 展示汇率 Mapper —— {@code exchange_rate} 表（一行一币，业务唯一键 {@code uk_code}）。
 */
@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRatePO> {
}
