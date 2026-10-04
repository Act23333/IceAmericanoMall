package org.icedamericanomall.wallet.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包流水 Mapper —— 入参/出参均为 PO。
 *
 * <p>幂等前置判定用 {@code selectCount}（{@code Transaction_no} 唯一索引覆盖，等值命中）；
 * 唯一键兜底由数据库承担，不在此做「先插后查」。
 */
@Mapper
public interface WalletTransactionMapper extends BaseMapper<WalletTransactionPO> {
}
