package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.AfterSaleEntity;
import org.icedAmericanoMall.domain.entity.SellerApplicationEntity;
import org.icedAmericanoMall.domain.entity.SettlementEntity;
import org.icedAmericanoMall.domain.entity.WithdrawalEntity;
import org.icedAmericanoMall.domain.vo.AfterSaleVO;
import org.icedAmericanoMall.domain.vo.SellerApplicationVO;
import org.icedAmericanoMall.domain.vo.SettlementVO;
import org.icedAmericanoMall.domain.vo.WithdrawalVO;
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
