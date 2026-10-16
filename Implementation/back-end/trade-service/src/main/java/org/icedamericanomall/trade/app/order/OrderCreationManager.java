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
import org.icedamericanomall.common.utils.IdGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * V4.1: 统一订单创建编排 Manager（京东/淘宝统一订单中心标准）。
 * 所有订单类型（NORMAL/DIRECT/FLASH_SALE/GROUP_BUY/PRESALE）通过策略模式路由到对应处理器。
 * 共享管道: 订单号生成 → validate → displaySnapshot(F16 fail-open) → buildOrder
 *           → deductStock → persist → afterCreate。
 *
 * <pre>
 * Scenario: 购物车下单 (orderType=NORMAL)
 * Scenario: 立即购买 (orderType=DIRECT)
 * Scenario: 秒杀订单 (orderType=FLASH_SALE, 由 marketing-service Feign 调用)
 * Scenario: 拼团订单 (orderType=GROUP_BUY, T27 两段式参团第一步，ADR-049)
 * Scenario: 订单创建失败 → Saga 补偿回滚库存 + 已用优惠券
 * Scenario: 券核销后建单失败（如 I43 运费 fail-closed）→ 全量回收券；进程崩溃 → 兜底对账回收
 * </pre>
 *
 * <p><b>V5.19 资损修复（券核销补偿）</b>：券核销是 buildOrder 内部的远程副作用
 * （marketing 侧 {@code CouponUsageManager#useCoupon} 自带 {@code @Transactional}，调用返回即已提交），
 * 而建单阶段其后仍有 fail-closed 步骤（I43 运费解析）。因此：
 * <ol>
 *   <li>buildOrder 与 deductStock 一并纳入补偿作用域（原先 buildOrder 在 try 之外，异常直接穿出）；</li>
 *   <li>补偿判据改为「券核销已发生」的<b>事实标记</b> {@code ctx.couponConsumed}
 *       （核销返回那一刻置位），不再用 {@code ctx.discountAmount > 0} 推导
 *       —— 金额可能为 0、且可能尚未回填（原缺陷的第二道失效）；</li>
 *   <li>库存回滚以「本次是否真的扣减成功」判定，避免把未扣减的库存加回去；</li>
 *   <li>下单进入核销阶段前落 {@link CouponConsumptionLedger} 兜底标记，进程异常退出时由
 *       {@link CouponReconcileManager} 定时对账回收。</li>
 * </ol>
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
    private final CouponConsumptionLedger couponConsumptionLedger;

    public OrderCreationManager(OrderRepository orderRepository, OrderConverter orderConverter,
                                 SkuClient skuClient, CouponClient couponClient,
                                 OrderTimeoutPublisher timeoutPublisher,
                                 OrderCreateStrategyFactory strategyFactory,
                                 DomainEventProducer eventProducer,
                                 OrderDisplaySnapshotService displaySnapshotService,
                                 CouponConsumptionLedger couponConsumptionLedger) {
        this.orderRepository = orderRepository;
        this.orderConverter = orderConverter;
        this.skuClient = skuClient;
        this.couponClient = couponClient;
        this.timeoutPublisher = timeoutPublisher;
        this.strategyFactory = strategyFactory;
        this.eventProducer = eventProducer;
        this.displaySnapshotService = displaySnapshotService;
        this.couponConsumptionLedger = couponConsumptionLedger;
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

        // 0. 统一订单号：在任何远程副作用（券核销）之前确定 —— 核销幂等锚点 + 补偿/兜底对账定位依据
        if (ctx.getOrderNo() == null) {
            ctx.setOrderNo(IdGenerator.nextId());
        }

        // 1. 策略校验（Feign 读取，事务外）
        strategy.validate(ctx);

        // 2. F16 多币种展示汇率快照（Feign I61，事务外，fail-open）——
        //    快照结果回填 ctx，随后 buildBaseOrder 随单落 orders.display_* 两列
        displaySnapshotService.snapshotIfNeeded(ctx);

        // 3. 兜底标记：本次将进入「可能核销券」的阶段（进程异常退出时由对账任务回收券）
        markCouponPending(ctx);

        // 4. 建单（含券核销 Feign）+ 扣库存（事务外）——
        //    buildOrder 内的核销是已提交的远程副作用，其后的任一失败都必须回收券
        Order order;
        List<StockOpCO> stockOps;
        try {
            order = strategy.buildOrder(ctx);
            stockOps = buildStockOps(ctx);
            deductStock(stockOps);
        } catch (RuntimeException e) {
            // 建单/扣库存阶段失败：库存必未扣减成功，券可能已核销 → 只回收券；
            // 原始异常原样上抛（保持既有错误码语义：I43/I44 降级码、STOCK_INSUFFICIENT 等不包装）
            compensateOnFailure(false, List.of(), null, ctx, e);
            throw e;
        }

        // 5. 持久化 + 策略后置处理：任一步失败都必须补偿已扣库存/券
        List<OrderItem> items;
        try {
            items = NormalCartOrderStrategy.buildItems(order, ctx);
            orderRepository.saveWithItems(order, items);
            publishTimeoutAfterCommit(order.getOrderNo());
            strategy.afterCreate(ctx, order);
        } catch (Exception e) {
            compensateOnFailure(true, stockOps, order, ctx, e);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单创建失败");
        }

        // 6. 订单已落库 → 券核销成为合法占用，兜底标记可清（清除失败不影响下单，对账侧会复核）
        clearCouponPending(ctx);

        // 7. RocketMQ 领域事件: order.created (非关键路径，事务外)
        publishOrderCreatedEvent(order);

        // 8. 返回 VO
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

    /**
     * Saga 补偿：回滚已扣库存 + 回收已核销优惠券（补偿失败记日志并需人工处理，不覆盖原始异常）。
     *
     * <p><b>补偿判据（V5.19）</b>：
     * <ul>
     *   <li>库存 —— 以「本次是否真的扣减成功」为据（未扣减就回滚 = 凭空加库存）；</li>
     *   <li>券 —— 以「核销副作用是否已发生」的<b>事实标记</b> {@code ctx.couponConsumed} 为据，
     *       与 {@code ctx.discountAmount} 无关（金额可能为 0，也可能尚未回填）。</li>
     * </ul>
     * 券侧结清后清除兜底对账标记；若回收失败则<b>保留</b>标记，交由
     * {@link CouponReconcileManager} 定时重试。
     *
     * @param stockDeducted 库存是否已实际扣减成功
     * @param stockOps      本次扣减的库存操作（未扣减时忽略）
     * @param order         订单对象（建单阶段失败时为 {@code null}）
     * @param ctx           下单上下文（券核销事实标记来源）
     * @param cause         触发补偿的原始异常（仅用于日志）
     */
    private void compensateOnFailure(boolean stockDeducted, List<StockOpCO> stockOps,
                                     Order order, OrderCreateContext ctx, Exception cause) {
        String orderNo = order != null && order.getOrderNo() != null ? order.getOrderNo() : ctx.getOrderNo();
        log.error("订单创建失败，触发 Saga 补偿: orderNo={}, stockDeducted={}, couponConsumed={}",
                orderNo, stockDeducted, ctx.isCouponConsumed(), cause);

        if (stockDeducted) {
            try {
                skuClient.restoreStock(stockOps);
            } catch (Exception re) {
                log.error("库存回滚失败！需人工处理: stockOps={}", stockOps, re);
            }
        } else {
            log.info("库存尚未扣减，跳过库存回滚: orderNo={}", orderNo);
        }

        if (reclaimConsumedCoupons(ctx, orderNo)) {
            clearCouponPending(ctx);
        }
    }

    /**
     * 回收已核销优惠券（依据「副作用已发生」的事实标记，与抵扣金额无关）。
     *
     * @return true = 券侧已结清（本次无券核销、或全部回收成功）→ 兜底标记可清；
     *         false = 回收失败 → 保留兜底标记，由对账任务重试
     */
    private boolean reclaimConsumedCoupons(OrderCreateContext ctx, String fallbackOrderNo) {
        if (!ctx.isCouponConsumed()) {
            return true;
        }
        List<String> orderNos = couponRollbackOrderNos(ctx, fallbackOrderNo);
        if (orderNos.isEmpty()) {
            log.error("券核销事实已置位但无可用订单号，无法回收！需人工处理: userId={}", ctx.getUserId());
            return false;
        }
        boolean reclaimed = true;
        for (String orderNo : orderNos) {
            try {
                couponClient.rollbackByOrderNo(orderNo);
                log.warn("订单创建失败，已回收核销优惠券: orderNo={}", orderNo);
            } catch (Exception ce) {
                reclaimed = false;
                log.error("优惠券回滚失败！保留兜底对账标记待重试: orderNo={}", orderNo, ce);
            }
        }
        return reclaimed;
    }

    /** 待回收的订单号：核销时记录的实际订单号优先，缺失时回落到订单对象/上下文的订单号。 */
    private List<String> couponRollbackOrderNos(OrderCreateContext ctx, String fallbackOrderNo) {
        List<String> consumed = ctx.getCouponConsumedOrderNos();
        if (consumed != null && !consumed.isEmpty()) {
            return consumed;
        }
        return fallbackOrderNo == null ? List.of() : List.of(fallbackOrderNo);
    }

    /**
     * 写入兜底对账标记（仅券单）—— 兜底网写入失败<b>不阻断下单</b>：
     * 主流程内的 Saga 补偿仍然生效，标记只覆盖「进程异常退出」这一补偿到不了的情形。
     */
    private void markCouponPending(OrderCreateContext ctx) {
        if (!ctx.hasCouponRequested()) {
            return;
        }
        try {
            couponConsumptionLedger.markPending(ctx.getOrderNo());
        } catch (Exception e) {
            log.warn("券核销兜底标记写入失败（不阻断下单，主流程补偿仍生效）: orderNo={}", ctx.getOrderNo(), e);
        }
    }

    /** 清除兜底对账标记（订单已成交或券已结清）；清除失败仅记日志，对账侧会复核订单存在性。 */
    private void clearCouponPending(OrderCreateContext ctx) {
        if (!ctx.hasCouponRequested()) {
            return;
        }
        try {
            couponConsumptionLedger.clear(ctx.getOrderNo());
        } catch (Exception e) {
            log.warn("券核销兜底标记清除失败（不影响下单）: orderNo={}", ctx.getOrderNo(), e);
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
