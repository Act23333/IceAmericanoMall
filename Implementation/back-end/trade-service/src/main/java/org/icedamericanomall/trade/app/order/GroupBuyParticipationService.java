package org.icedamericanomall.trade.app.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.GroupBuyClient;
import org.icedamericanomall.client.OrderRefundClient;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.dto.GroupJoinCO;
import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.GroupTeamRefundCO;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.trade.domain.enums.OrderTypeEnum;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.model.order.OrderItem;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 拼团参团/结团退款编排协作器（F13, ADR-049 两段式参团第二步 + 结团退款联动）。
 *
 * <pre>
 * Scenario: 拼团单支付成功（1→2 条件迁移落库后、事务外）→ I53 参团/开团（teamNo 空=开团）
 * Scenario: 参团失败（团满/已结/重复/服务降级，fail-closed）→ 条件取消(2→5) + 恢复库存 + I55 退款闭环
 * Scenario: 参团上下文缺失（Redis 键过期/丢失/故障）→ 同上退款闭环，绝不静默假装参团成功
 * Scenario: 团超时结团（marketing I54）→ 逐单「取消+恢复库存+退款」，单笔失败继续不中断整团
 * Scenario: 非拼团单支付成功 → 直接返回，零额外动作
 * </pre>
 *
 * <p><b>事务与异常边界</b>：本类不加 {@code @Transactional}（管道含 Feign，硬约束）；退款闭环
 * 本地取消成功后方法正常返回——I55 失败仅 log.error 告警（退款单停留失败态由既有补偿语义收敛），
 * 绝不向 pay 回调调用方抛异常。库存恢复与 {@code OrderLifecycleManager} 同口径
 * （失败记日志需人工处理）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyParticipationService {

    /** I55 幂等业务键前缀（refund_order.uk_biz_ref，ADR-049：拼团场景 = GB:{orderNo}，一单一次退款） */
    static final String SCENE_REF_PREFIX = "GB:";

    /** 参团失败/结团自动退款原因摘要 */
    static final String REFUND_REASON = "拼团参团失败自动退款";

    private final OrderRepository orderRepository;
    private final SkuClient skuClient;
    private final GroupBuyClient groupBuyClient;
    private final OrderRefundClient orderRefundClient;
    private final GroupBuyJoinContextStore joinContextStore;

    /**
     * 支付成功参团钩子（订单 1→2 条件迁移成功后、事务外调用，非拼团单直接返回）。
     *
     * <p>参团上下文（activityId/teamNo）由 T27 下单期写入 Redis（{@link GroupBuyJoinContextStore}），
     * 此处一次性消费；缺失或 I53 join 抛异常（fail-closed）均触发自动退款闭环。
     *
     * @param orderNo 拼团订单号
     */
    public void participateAfterPayment(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo);
        if (order == null || order.getOrderType() == null
                || order.getOrderType() != OrderTypeEnum.GROUP_BUY.getCode()) {
            return;
        }
        GroupBuyJoinContextStore.GroupBuyJoinCtx ctx = joinContextStore.take(orderNo);
        if (ctx == null || ctx.getActivityId() == null) {
            log.error("拼团参团上下文缺失（键过期/丢失/Redis 故障），按不可参团处理并退款闭环: orderNo={}",
                    orderNo);
            cancelAndRefund(order);
            return;
        }
        try {
            GroupTeamCO team = groupBuyClient.join(new GroupJoinCO(
                    order.getUserId(), orderNo, ctx.getActivityId(), ctx.getTeamNo()));
            log.info("拼团参团成功: orderNo={}, activityId={}, teamNo={}, 回执status={}/{}人",
                    orderNo, ctx.getActivityId(), ctx.getTeamNo(),
                    team == null ? null : team.getStatus(), team == null ? null : team.getRequiredMembers());
        } catch (Exception e) {
            log.error("拼团参团失败（fail-closed），触发自动退款闭环: orderNo={}", orderNo, e);
            cancelAndRefund(order);
        }
    }

    /**
     * I54 结团退款联动（marketing {@code GroupBuyExpireJob} 把超时团置已失败后触发）。
     *
     * <p>逐单执行「条件取消（2→5）+ 恢复库存 + I55 全额退款」；单笔失败 try/catch 继续，
     * 不中断整团（失败单依赖 refund_order 失败态 + 后续补偿，ADR-049）。
     *
     * @param refund 结团退款入参（teamNo 审计定位 + 团成员订单号列表）
     */
    public void refundFailedTeam(GroupTeamRefundCO refund) {
        if (refund == null || refund.getOrderNos() == null || refund.getOrderNos().isEmpty()) {
            log.info("结团退款联动入参为空，跳过: teamNo={}", refund == null ? null : refund.getTeamNo());
            return;
        }
        for (String orderNo : refund.getOrderNos()) {
            try {
                Order order = orderRepository.findByOrderNo(orderNo);
                if (order == null) {
                    log.info("结团退款跳过（订单不存在）: teamNo={}, orderNo={}", refund.getTeamNo(), orderNo);
                    continue;
                }
                cancelAndRefund(order);
            } catch (Exception e) {
                log.error("结团退款单笔失败，继续处理其余订单: teamNo={}, orderNo={}",
                        refund.getTeamNo(), orderNo, e);
            }
        }
    }

    /**
     * 退款闭环：条件取消（待发货 2→5，幂等：非待发货 0 行跳过）→ 恢复库存 → I55 按订单全额退款。
     * 本地取消成功后方法正常返回（退款失败仅告警），绝不向调用方抛异常。
     */
    private void cancelAndRefund(Order order) {
        boolean cancelled = orderRepository.cancelPaidBySystem(order.getOrderNo());
        if (!cancelled) {
            log.info("拼团退款闭环跳过（订单非待发货，条件取消 0 行）: orderNo={}, status={}",
                    order.getOrderNo(), order.getStatus());
            return;
        }
        restoreStock(order.getId());
        refundByOrder(order.getOrderNo());
    }

    /** 恢复订单库存（与 {@code OrderLifecycleManager} 同口径：失败记日志需人工处理，不阻断闭环）。 */
    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderRepository.findItemsByOrderId(orderId);
        if (items.isEmpty()) return;
        List<StockOpCO> stockOps = items.stream().map(item -> {
            StockOpCO op = new StockOpCO();
            op.setSkuId(item.getSkuId());
            op.setQuantity(item.getQuantity());
            return op;
        }).collect(Collectors.toList());
        try {
            skuClient.restoreStock(stockOps);
            log.info("拼团退款闭环库存已恢复: orderId={}", orderId);
        } catch (Exception e) {
            log.error("拼团退款闭环库存恢复失败，需人工处理: orderId={}", orderId, e);
        }
    }

    /** I55 按订单全额退款（fail-closed 资金命令：异常吞掉告警，退款单停留失败态由补偿语义收敛）。 */
    private void refundByOrder(String orderNo) {
        try {
            orderRefundClient.refundByOrder(
                    new OrderRefundCO(orderNo, SCENE_REF_PREFIX + orderNo, REFUND_REASON));
            log.info("拼团自动退款已发起: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("拼团自动退款发起失败（退款单停留失败态，依赖补偿收敛）: orderNo={}", orderNo, e);
        }
    }
}
