package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CouponClient 降级：marketing-service 不可用时，下单不抵扣（fail-safe，用户不丢券）。
 */
@Slf4j
@Component
public class CouponClientFallback implements FallbackFactory<CouponClient> {
    @Override
    public CouponClient create(Throwable cause) {
        return new CouponClient() {
            @Override
            public Integer useCoupon(Long userId, Long userCouponId, String orderNo,
                                      Integer orderAmount, Integer orderType, Long sellerId,
                                      String productIds, String categoryIds) {
                log.error("优惠券使用失败，降级为不抵扣: userCouponId={}, orderNo={}", userCouponId, orderNo, cause);
                return 0;
            }

            @Override
            public void rollbackByOrderNo(String orderNo) {
                log.error("优惠券回滚失败，需人工处理: orderNo={}", orderNo, cause);
            }

            @Override
            public List<Object> batchGet(List<Long> ids) {
                log.error("批量查询券模板失败: ids={}", ids, cause);
                return List.of();
            }
        };
    }
}
