package org.icedamericanomall.trade.convert;

import org.icedamericanomall.trade.domain.entity.AfterSaleEntity;
import org.icedamericanomall.trade.domain.entity.SellerApplicationEntity;
import org.icedamericanomall.trade.domain.entity.SettlementEntity;
import org.icedamericanomall.trade.domain.entity.WithdrawalEntity;
import org.icedamericanomall.trade.domain.vo.AfterSaleVO;
import org.icedamericanomall.trade.domain.vo.SellerApplicationVO;
import org.icedamericanomall.trade.domain.vo.SettlementVO;
import org.icedamericanomall.trade.domain.vo.WithdrawalVO;
import org.mapstruct.Mapper;

/**
 * 交易域 售后/入驻/结算/提现 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface TradeConverter {

    AfterSaleVO toVO(AfterSaleEntity entity);

    SellerApplicationVO toVO(SellerApplicationEntity entity);

    SettlementVO toVO(SettlementEntity entity);

    WithdrawalVO toVO(WithdrawalEntity entity);
}
