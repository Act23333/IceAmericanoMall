package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.AfterSaleEntity;
import org.icedamericanomall.domain.entity.SellerApplicationEntity;
import org.icedamericanomall.domain.entity.SettlementEntity;
import org.icedamericanomall.domain.entity.WithdrawalEntity;
import org.icedamericanomall.domain.vo.AfterSaleVO;
import org.icedamericanomall.domain.vo.SellerApplicationVO;
import org.icedamericanomall.domain.vo.SettlementVO;
import org.icedamericanomall.domain.vo.WithdrawalVO;
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
