package org.icedamericanomall.trade.app.order;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.trade.adapter.assembler.OrderConverter;
import org.icedamericanomall.trade.adapter.request.GroupBuyOrderReq;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.adapter.response.OrderVO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.infrastructure.messaging.DomainEventProducer;
import org.icedamericanomall.trade.infrastructure.messaging.OrderTimeoutPublisher;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.app.order.strategy.NormalCartOrderStrategy;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategy;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateStrategyFactory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.event.OrderCreatedEvent;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * V4.1: 统一订单创建编排 Manager（京东/淘宝统一订单中心标准）。
 * 所有订单类型（NORMAL/DIRECT/FLASH_SALE/GROUP_BUY/PRESALE）通过策略模式路由到对应处理器。
 * 共享管道: validate → displaySnapshot(F16 fail-open) → buildOrder → deductStock → persist → afterCreate。
 *
 * <pre>
 * Scenario: 购物车下单 (orderType=NORMAL)
 * Scenario: 立即购买 (orderType=DIRECT)
 * Scenario: 秒杀订单 (orderType=FLASH_SALE, 由 marketing-service Feign 调用)
 * Scenario: 拼团订单 (orderType=GROUP_BUY, T27 两段式参团第一步，ADR-049)
 * Scenario: 订单创建失败 → Saga 补偿回滚库存 + 已用优惠券
 * </pre>
 */
@Slf4j
@Component
public class OrderCreationManager {

    /** 受保护资源名 —— 必须与 {@code SentinelConfig} 注册的规则资源名一致。 */
    public static final String RESOURCE_CREATE_ORDER = "createOrder";

    private final OrderRepository orderRepository;
    private final OrderConverter orderConverter;
    private final SkuClient skuClient;
    private final CouponClient couponClient;
    private final OrderTimeoutPublisher timeoutPublisher;
    private final OrderCreateStrategyFactory strategyFactory;
    private final DomainEventProducer eventProducer;
    private final OrderDisplaySnapshotService displaySnapshotService;

    public OrderCreationManager(OrderRepository orderRepository, OrderConverter orderConverter,
                                 SkuClient skuClient, CouponClient couponClient,
                                 OrderTimeoutPublisher timeoutPublisher,
                                 OrderCreateStrategyFactory strategyFactory,
                                 DomainEventProducer eventProducer,
                                 OrderDisplaySnapshotService displaySnapshotService) {
        this.orderRepository = orderRepository;
        this.orderConverter = orderConverter;
        this.skuClient = skuClient;
        this.couponClient = couponClient;
        this.timeoutPublisher = timeoutPublisher;
        this.strategyFactory = strategyFactory;
        this.eventProducer = eventProducer;
        this.displaySnapshotService = displaySnapshotService;
    }

    /**
     * V4.1: 统一订单创建入口。
     * 根据 ctx.orderType 路由到对应策略，执行标准创建管道。
     *
     * <p><b>事务边界</b>：本方法整体<b>不</b>加 {@code @Transactional} —— 管道内含
     * Feign 调用（扣库存、核销优惠券、清购物车），事务内调 Feign 会长时间占用数据库连接
     * （AGENTS.md 硬约束）。仅「订单 + 订单项持久化」这一步由
     * {@link OrderService#createOrderWithItems} 自身的本地事务保证原子。
     *
     * <p><b>Saga 补偿</b>：扣库存之后的所有步骤（持久化、策略后置处理）任一步失败，
     * 统一回滚已扣库存与已核销优惠券。
     *
     * <p><b>Sentinel 保护（V5.4 接线）</b>：本方法以资源名 {@code createOrder} 受
     * {@code @SentinelResource} 保护，规则由 {@code SentinelConfig} 注册：
     * <ul>
     *   <li><b>flow（流控）</b>——QPS 越线即 block，不进入下单管道；</li>
     *   <li><b>degrade（熔断）</b>——窗口内异常比例越线后熔断打开，同样直接 block。</li>
     * </ul>
     * 两个维度统一由 {@link #createOrderBlockHandler(OrderCreateContext, BlockException)}
     * 转为 {@code BizException(FREQUENT_ERROR)}（HTTP 429）。{@code BlockException} 不参与
     * Sentinel 异常统计（Sentinel 语义），故 blockHandler 抛出的业务异常不会回灌熔断统计。
     *
     * @param ctx 下单上下文（用户、订单类型、地址/优惠券等）
     * @return 新建订单视图（含订单项）
     * @throws BizException 库存不足（STOCK_INSUFFICIENT）/ 下单失败（BUSINESS_EXECUTION_EXCEPTION）/
     *                      流控或熔断命中（FREQUENT_ERROR）
     */
    @SentinelResource(value = RESOURCE_CREATE_ORDER, blockHandler = "createOrderBlockHandler")
    public OrderVO createOrder(OrderCreateContext ctx) {
        OrderCreateStrategy strategy = strategyFactory.getStrategy(ctx.getOrderType());

        // 1. 策略校验（Feign 读取，事务外）
        strategy.validate(ctx);

        // 2. F16 多币种展示汇率快照（Feign I61，事务外，fail-open）——
        //    快照结果回填 ctx，随后 buildBaseOrder 随单落 orders.display_* 两列
        displaySnapshotService.snapshotIfNeeded(ctx);

        // 3. 策略构建订单（含优惠券核销 Feign，事务外）
        Order order = strategy.buildOrder(ctx);

        // 4. 扣减库存（Feign，事务外）
        List<StockOpCO> stockOps = buildStockOps(ctx);
        deductStock(stockOps);

        // 5. 持久化 + 策略后置处理：任一步失败都必须补偿已扣库存/券
        List<OrderItem> items = NormalCartOrderStrategy.buildItems(order, ctx);
        try {
            orderRepository.saveWithItems(order, items);
            publishTimeoutAfterCommit(order.getOrderNo());
            strategy.afterCreate(ctx, order);
        } catch (Exception e) {
            compensateOnFailure(stockOps, order, ctx, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // 6. RocketMQ 领域事件: order.created (非关键路径，事务外)
        publishOrderCreatedEvent(order);

        // 7. 返回 VO
        OrderVO vo = orderConverter.toVO(order);
        vo.setItems(orderConverter.toItemVOs(items));
        return vo;
    }

    /**
     * Sentinel blockHandler —— 流控（flow）与熔断（degrade）命中的统一出口。
     *
     * <p>由 {@code @SentinelResource.blockHandler} 反射调用（同一类、public、非静态，
     * 形参表为「原方法形参 + {@link BlockException}」）。转换为
     * {@code BizException(FREQUENT_ERROR)}：{@code FREQUENT_ERROR} 的 HTTP 状态为 429
     * （Too Many Requests），与限流语义一致，前端可据此退避重试。
     *
     * <p>此处抛出的业务异常不会回灌 Sentinel 的异常统计，因此不会出现
     * 「被限流 → 统计为异常 → 触发熔断」的级联误判。
     *
     * @param ctx 原方法入参（下单上下文，占位以匹配签名）
     * @param ex  Sentinel 抛出的阻塞异常（FlowException / DegradeException 等）
     * @return 不返回；恒抛异常
     * @throws BizException 恒定抛出，错误码 {@code FREQUENT_ERROR}
     */
    public OrderVO createOrderBlockHandler(OrderCreateContext ctx, BlockException ex) {
        log.warn("下单被 Sentinel 拦截: resource={}, ruleLimitApp={}, userId={}",
                RESOURCE_CREATE_ORDER, ex.getRuleLimitApp(),
                ctx == null ? null : ctx.getUserId(), ex);
        throw new BizException(ErrorCode.FREQUENT_ERROR, "下单请求过于频繁，请稍后重试");
    }

    /**
     * F13 拼团下单（T27, ADR-049 两段式参团第一步）—— 组装拼团上下文后走统一创建管道。
     *
     * <p>quantity 恒 1（一人一单一件）；拼团价由 I52 validate 服务端下发并快照，
     * 不叠加券/促销/会员（与秒杀同口径，ADR-043）；运费走常规计价。下单<b>不占团位</b>
     * （未支付走既有 30 分钟超时取消），支付成功后才经 I53 入团
     * （见 {@code GroupBuyParticipationService#participateAfterPayment}）。
     *
     * @param userId 买家 user_id
     * @param req    拼团下单请求（活动 id 必填；团号可空 = 开团）
     * @return 新建订单视图（含订单项）
     * @throws BizException I52 校验失败 / SKU 或地址不存在 / 库存不足 /
     *                      参团上下文写入失败 / 流控熔断（FREQUENT_ERROR）
     */
    public OrderVO createGroupBuyOrder(Long userId, GroupBuyOrderReq req) {
        OrderCreateContext ctx = new OrderCreateContext();
        ctx.setUserId(userId);
        ctx.setOrderType(OrderTypeEnum.GROUP_BUY);
        ctx.setActivityId(req.getActivityId());
        ctx.setTeamNo(req.getTeamNo());
        ctx.setAddressId(req.getAddressId());
        ctx.setRemark(req.getRemark());
        ctx.setDisplayCurrency(req.getDisplayCurrency()); // F16: 展示币种透传（快照走统一管道）
        return createOrder(ctx);
    }

    /** 扣减库存（Feign），失败即终止下单。 */
    private void deductStock(List<StockOpCO> stockOps) {
        try {
            skuClient.deductStock(stockOps);
        } catch (Exception e) {
            log.error("库存扣减失败", e);
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, "库存扣减失败，请重试");
        }
    }

    /** Saga 补偿：回滚已扣库存 + 已核销优惠券（补偿失败记日志并需人工处理，不覆盖原始异常）。 */
    private void compensateOnFailure(List<StockOpCO> stockOps, Order order,
                                     OrderCreateContext ctx, Exception cause) {
        log.error("订单创建失败，回滚库存: orderNo={}", order.getOrderNo(), cause);
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
    }

    private void publishOrderCreatedEvent(Order order) {
        try {
            eventProducer.publishOrderCreated(new OrderCreatedEvent(
                    order.getOrderNo(), order.getUserId(),
                    order.getPayAmount(), "WECHAT"));
        } catch (Exception e) {
            log.warn("订单创建事件发布失败(order.created): orderNo={}", order.getOrderNo(), e);
        }
    }

    private List<StockOpCO> buildStockOps(OrderCreateContext ctx) {
        List<StockOpCO> ops = new ArrayList<>();
        for (var snap : ctx.getSnapshots()) {
            StockOpCO op = new StockOpCO();
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
