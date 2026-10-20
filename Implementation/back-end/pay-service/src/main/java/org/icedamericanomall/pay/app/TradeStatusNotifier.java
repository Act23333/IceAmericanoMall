package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.pay.domain.enums.TradeOrderStatusEnum;
import org.springframework.stereotype.Component;

/**
 * 跨服务订单状态回写（Feign 到 trade-service）。
 *
 * <p><b>为什么单独成类</b>：同一段「回写订单状态 + fail-open 日志」被两条互不相关的用例族共用 ——
 * 支付成功（{@code PayManager#payByBalance}、回调确认收款）与支付超时（{@code cancelTimeoutPayOrders}）。
 * 原先它作为私有方法挂在 {@code PayManager} 上，导致回调族拆分时必须二选一：要么把回调族留在
 * 344 行的类里（类长度棘轮不让），要么复制这段 Feign（复制「失败只记日志」的降级口径是事故源）。
 * 抽成协作者后两侧各自持有同一实现，**降级口径只有一份**。
 *
 * <p><b>失败口径（刻意的 fail-open）</b>：回写失败只记 ERROR 不外抛 —— 调用点持有的都是
 * 「本地状态已提交」的事实（钱已扣 / 支付单已置成功 / 支付单已取消），把远端抖动升级成异常，
 * 会让一次**已生效**的本地结果对外表现为失败。收敛路径在 trade 侧（订单超时兜底任务）
 * 与对账，不靠调用方重试。
 *
 * <p><b>事务纪律</b>：本类只做 Feign 调用，<b>不加</b> {@code @Transactional}
 * （事务内调 Feign 属硬约束违规，见 {@code collaboration/09-constraints.md}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeStatusNotifier {

    private final OrderClient orderClient;

    /** 通知 trade-service 订单进入待发货（非致命，失败可对账修复）。 */
    public void notifyPaid(String orderNo) {
        try {
            orderClient.updateOrderStatus(orderNo, TradeOrderStatusEnum.PENDING_SHIPMENT.getCode());
            log.info("支付成功，订单状态已更新: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("支付成功但订单状态更新失败: orderNo={}", orderNo, e);
        }
    }

    /** 通知 trade-service 取消订单（非致命，超时订单也可由 trade 侧兜底任务取消）。 */
    public void notifyCancelled(String orderNo) {
        try {
            orderClient.updateOrderStatus(orderNo, TradeOrderStatusEnum.CANCELLED.getCode());
        } catch (Exception e) {
            log.error("支付超时取消订单失败: orderNo={}", orderNo, e);
        }
    }
}
