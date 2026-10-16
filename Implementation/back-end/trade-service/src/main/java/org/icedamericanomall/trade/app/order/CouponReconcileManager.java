package org.icedamericanomall.trade.app.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 券核销兜底对账（V5.19 资损修复）—— 回收「主流程未走完、补偿也没跑到」的已核销券。
 *
 * <p><b>覆盖的失败形态</b>：trade 进程在下单中途死亡（容器驱逐 / OOM / JVM 崩溃），
 * marketing 侧核销事务已提交、trade 侧订单未落库且无补偿执行者。此时
 * {@link CouponConsumptionLedger} 中的「处理中」标记会残留，由本类定时扫描回收。
 *
 * <p><b>判定方向（安全第一）</b>：
 * <ul>
 *   <li>trade 侧<b>查得到</b>该订单 → 券核销是合法占用，只清标记，<b>绝不回滚</b>；</li>
 *   <li>trade 侧<b>查不到</b>该订单（订单确实未创建）→ 调用契约 I23
 *       {@code POST /internal/coupon/rollback}（marketing 侧 {@code findUsedByOrderNo} + restore
 *       幂等，未命中即空操作）；</li>
 *   <li>查询或回滚任一步异常（DB/Feign 不可用）→ <b>保留标记</b>下轮重试：
 *       宁可不处理，也不能在状态不确定时清除标记（清除即等价于放弃回收）。</li>
 * </ul>
 *
 * <p><b>可重入</b>：标记清除、券回滚均为幂等操作，任务重复调度 / 并发触发不会造成重复回收。
 *
 * <p><b>宽限窗口</b>：只处理写入时间早于 {@code now - GRACE} 的标记 —— 更年轻的标记对应的下单
 * 请求可能仍在进行中，避免与在途流程竞态（对账任务是兜底，不参与快路径）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponReconcileManager {

    /** 宽限窗口：跳过刚写入的标记（在途下单请求仍在核销-落库窗口内）。 */
    static final Duration GRACE = Duration.ofMinutes(10);

    /** 单轮处理上限（防一次调度打爆下游）。 */
    static final int BATCH_SIZE = 200;

    private final CouponConsumptionLedger couponConsumptionLedger;
    private final OrderRepository orderRepository;
    private final CouponClient couponClient;

    /**
     * 执行一轮对账：回收「无对应订单」的已核销券，清理「订单已成交」的过期标记。
     *
     * @return 本轮实际发起券回收的订单数（含 marketing 侧空操作的「可能已核销」标记）
     */
    public int reconcile() {
        long cutoff = System.currentTimeMillis() - GRACE.toMillis();
        List<String> pending = couponConsumptionLedger.listPendingBefore(cutoff, BATCH_SIZE);
        if (pending.isEmpty()) {
            return 0;
        }

        int reclaimed = 0;
        for (String orderNo : pending) {
            try {
                if (orderRepository.findByOrderNo(orderNo) != null) {
                    couponConsumptionLedger.clear(orderNo);
                    continue;
                }
                couponClient.rollbackByOrderNo(orderNo);
                couponConsumptionLedger.clear(orderNo);
                reclaimed++;
                log.warn("兜底对账回收已核销优惠券: orderNo={}", orderNo);
            } catch (Exception e) {
                // 状态不确定（订单查询失败 / 回滚失败）→ 保留标记，下轮重试
                log.error("兜底对账处理失败，保留标记待下轮重试: orderNo={}", orderNo, e);
            }
        }
        log.info("券核销兜底对账完成: scanned={}, reclaimed={}", pending.size(), reclaimed);
        if (reclaimed > 0) {
            log.warn("ALERT: 兜底对账回收 {} 张券（存在下单中途异常退出的订单），建议人工核查", reclaimed);
        }
        return reclaimed;
    }
}
