package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.SettlementEntity;

public interface SettlementService extends IService<SettlementEntity> {
    /** 生成商家结算单（管理员触发） */
    SettlementEntity generate(Long sellerId, String periodStart, String periodEnd);
    /** 结算单分页 */
    IPage<SettlementEntity> pageBySeller(Long sellerId, int page, int size);
}
