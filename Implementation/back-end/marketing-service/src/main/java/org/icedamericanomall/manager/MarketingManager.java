package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.constants.GrabTypeEnum;
import org.icedamericanomall.domain.dto.FlashBuyResult;
import org.icedamericanomall.domain.dto.FlashSaleOrderMessage;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryDTO;
import org.icedamericanomall.producer.FlashSaleOrderPublisher;
import org.icedamericanomall.service.CouponService;
import org.icedamericanomall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V5.0 DDD: 营销编排 Manager（Application 层）。
 *
 * 职责: 秒杀+优惠券用例编排，跨 Service 聚合 + 跨服务 Feign 调用。
 * Service 层不调 Feign — Feign/MQ 全部在 Manager 层完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingManager {

    private final FlashSaleService flashSaleService;
    private final CouponService couponService;
    private final OrderClient orderClient;
    private final FlashSaleOrderPublisher publisher;

    /** 秒杀列表: Controller→Manager→Service */
    public List<FlashSaleEntity> listActiveFlashSales() {
        return flashSaleService.listActive();
    }

    /**
     * V5.0 DDD: 秒杀购买全链路编排。
     *
     * 1. Service: Redis Lua 预扣库存（纯领域操作）
     * 2. Manager: Feign 调用 trade-service 创建真实订单（跨服务编排）
     * 3. Manager: RocketMQ 异步统计（非关键路径）
     *
     * Service 不调 Feign，Feign 归属 Manager 层。
     */
    public FlashBuyResult buyFlashSale(Long flashId, Long addressId) {
        // 1. 领域服务：Redis Lua 预扣
        FlashBuyResult preResult = flashSaleService.buy(flashId, addressId);

        // 2. Manager 编排：Feign 调用 trade-service 创建订单
        Long userId = UserContext.getUserId();
        CreateOrderInternalReq req = new CreateOrderInternalReq();
        req.setUserId(userId);
        req.setSkuId(preResult.getSkuId());
        req.setSellerId(0L);
        req.setQuantity(1);
        req.setFlashPrice(preResult.getFlashPrice());
        req.setFlashId(flashId);
        req.setAddressId(addressId);

        OrderSummaryDTO order;
        try {
            order = orderClient.createOrder(req);
            if (order == null) throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 trade-service 创建秒杀订单失败: flashId={}, userId={}", flashId, userId, e);
            throw new BizException(ErrorCode.FLASH_SALE_FAILED);
        }

        preResult.setOrderNo(order.getOrderNo());

        // 3. Manager 编排：MQ 异步统计（非关键路径，失败不影响主流程）
        FlashSaleOrderMessage msg = new FlashSaleOrderMessage();
        msg.setOrderNo(order.getOrderNo());
        msg.setFlashId(flashId);
        msg.setUserId(userId);
        msg.setSkuId(preResult.getSkuId());
        msg.setQuantity(1);
        try {
            publisher.publish(msg);
        } catch (Exception e) {
            log.error("Flash MQ publish failed (non-critical): flashId={}, orderNo={}",
                    flashId, order.getOrderNo(), e);
        }

        return preResult;
    }

    /**
     * V4.0: 智能优惠券领取 — 根据 grabType 自动选择领取通道。
     */
    public UserCouponEntity smartClaim(Long userId, String couponId) {
        CouponEntity coupon = couponService.lambdaQuery()
                .eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");

        if (coupon.getGrabType() != null && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()) {
            return couponService.claimWithGrab(userId, couponId);
        }
        return couponService.claim(userId, couponId);
    }
}
