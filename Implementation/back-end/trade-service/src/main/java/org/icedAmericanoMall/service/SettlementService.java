package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.SettlementEntity;

public interface SettlementService extends IService<SettlementEntity> {
    /** 生成商家结算单（管理员触发） */
    SettlementEntity generate(Long sellerId, String periodStart, String periodEnd);

    /** 商家结算单分页 */
    IPage<SettlementEntity> pageBySeller(Long sellerId, int page, int size);

    /** 商家结算单详情（含归属校验）。 */
    SettlementEntity getSellerSettlement(Long id, Long sellerId);

    /** 商家可提现余额 = 所有已结算未打款金额之和（单位：分）。 */
    int availableBalance(Long sellerId);

    /** 管理后台：全部结算单分页。 */
    IPage<SettlementEntity> pageAll(int page, int size);

    /** 提现打款成功后，将该商家已结算单据置为已打款。 */
    void markSellerSettlementsPaidOut(Long sellerId);
}
