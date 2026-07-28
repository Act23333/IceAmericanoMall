package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.service.CouponService;
import org.icedamericanomall.service.FlashSaleService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.0 DDD: 营销编排 Manager（Application层）。
 * 职责: 秒杀+优惠券用例编排，跨Service聚合。
 */
@Component
@RequiredArgsConstructor
public class MarketingManager {

    private final FlashSaleService flashSaleService;
    private final CouponService couponService;

    /** 秒杀列表: Controller→Manager→Service */
    public List<FlashSaleEntity> listActiveFlashSales() {
        return flashSaleService.listActive();
    }

    /** 秒杀购买: Manager编排 Redis(库存)→RocketMQ(异步)→DB(落库) */
    public boolean buyFlashSale(Long flashId) {
        return flashSaleService.buy(flashId);
    }
}
