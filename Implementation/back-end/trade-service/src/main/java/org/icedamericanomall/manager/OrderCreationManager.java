package org.icedamericanomall.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.convert.OrderConverter;
import org.icedamericanomall.domain.entity.OrderEntity;
import org.icedamericanomall.domain.entity.OrderItemEntity;
import org.icedamericanomall.domain.vo.OrderVO;
import org.icedamericanomall.dto.StockOpDTO;
import org.icedamericanomall.producer.OrderTimeoutPublisher;
import org.icedamericanomall.service.OrderService;
import org.icedamericanomall.strategy.NormalCartOrderStrategy;
import org.icedamericanomall.strategy.OrderCreateContext;
import org.icedamericanomall.strategy.OrderCreateStrategy;
import org.icedamericanomall.strategy.OrderCreateStrategyFactory;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * V4.1: 统一订单创建编排 Manager（京东/淘宝统一订单中心标准）。
 * 所有订单类型（NORMAL/DIRECT/FLASH_SALE/PRESALE）通过策略模式路由到对应处理器。
 * 共享管道: validate → buildOrder → deductStock → persist → afterCreate。
 *
 * <pre>
 * Scenario: 购物车下单 (orderType=NORMAL)
 * Scenario: 立即购买 (orderType=DIRECT)
 * Scenario: 秒杀订单 (orderType=FLASH_SALE, 由 marketing-service Feign 调用)
 * Scenario: 订单创建失败 → Saga 补偿回滚库存 + 已用优惠券
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreationManager {

    private final OrderService orderService;
    private final OrderConverter orderConverter;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final OrderTimeoutPublisher timeoutPublisher;
    private final OrderCreateStrategyFactory strategyFactory;

    /**
     * V4.1: 统一订单创建入口。
     * 根据 ctx.orderType 路由到对应策略，执行标准创建管道。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(OrderCreateContext ctx) {
        OrderCreateStrategy strategy = strategyFactory.getStrategy(ctx.getOrderType());

        // 1. 策略校验
        strategy.validate(ctx);

        // 2. 策略构建订单
        OrderEntity order = strategy.buildOrder(ctx);

        // 3. 扣减库存
        List<StockOpDTO> stockOps = buildStockOps(ctx);
        try {
            skuClient.deductStock(stockOps);
        } catch (Exception e) {
            log.error("库存扣减失败", e);
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "库存扣减失败，请重试");
        }

        // 4. 持久化订单 + 订单项
        List<OrderItemEntity> items = NormalCartOrderStrategy.buildItems(order, ctx);
        try {
            orderService.createOrderWithItems(order, items);
            publishTimeoutAfterCommit(order.getOrderNo());
        } catch (Exception e) {
            // Saga 补偿
            log.error("订单创建失败，回滚库存", e);
            try {
                skuClient.restoreStock(stockOps);
            } catch (Exception re) {
                log.error("库存回滚失败！需人工处理: stockOps={}", stockOps, re);
            }
            if (ctx.getDiscountAmount() > 0) {
                try {
                    couponClient.rollbackByOrderNo(order.getOrderNo());
                } catch (Exception ce) {
                    log.error("优惠券回滚失败！需人工处理: orderNo={}", order.getOrderNo(), ce);
                }
            }
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // 5. 策略后置处理
        strategy.afterCreate(ctx, order);

        // 6. 返回 VO
        OrderVO vo = orderConverter.entityToVO(order);
        vo.setItems(orderConverter.itemEntitiesToVOs(items));
        return vo;
    }

    private List<StockOpDTO> buildStockOps(OrderCreateContext ctx) {
        List<StockOpDTO> ops = new ArrayList<>();
        for (var snap : ctx.getSnapshots()) {
            StockOpDTO op = new StockOpDTO();
            op.setSkuId(snap.getSkuId());
            op.setQuantity(snap.getQuantity());
            ops.add(op);
        }
        return ops;
    }

    private void publishTimeoutAfterCommit(String orderNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            timeoutPublisher.publishTimeout(orderNo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                timeoutPublisher.publishTimeout(orderNo);
            }
        });
    }
}
