package org.icedamericanomall.trade.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.order.CouponReconcileManager;
import org.springframework.stereotype.Component;

/**
 * 券核销兜底对账任务（V5.19 资损修复）—— XXL-Job 定时触发，建议 5 分钟一次。
 *
 * <p><b>为何需要它</b>：下单链路的 Saga 补偿只在「异常可捕获」时生效；trade 进程在下单中途
 * 异常退出（容器驱逐 / OOM / JVM 崩溃）时，marketing 侧券核销已提交而订单未落库，
 * 无任何执行者会回收该券 → 用户资产静默损失。本任务扫描
 * {@link org.icedamericanomall.trade.app.order.CouponConsumptionLedger} 的残留标记，
 * 在确认 trade 侧无对应订单后回收券（可重入、幂等）。
 *
 * <p>处理逻辑在应用层 {@link CouponReconcileManager}（四层依赖方向：infrastructure → app）。
 * 本任务只做触发与日志，与 {@code AbnormalOrderJob}（超时订单第三层兜底）同构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRollbackReconcileJob {

    private final CouponReconcileManager couponReconcileManager;

    /** XXL-Job 任务名：券核销兜底对账（建议调度频率 5 分钟，见 ADR 交接摘要）。 */
    @XxlJob("reconcileCouponConsumption")
    public void reconcileCouponConsumption() {
        log.info("XXL-Job: reconcileCouponConsumption triggered");
        try {
            int reclaimed = couponReconcileManager.reconcile();
            log.info("reconcileCouponConsumption done: reclaimed={}", reclaimed);
        } catch (Exception e) {
            // 任务级异常只记录不抛出：下一轮调度会重试（标记未清除即待回收集合不变）
            log.error("reconcileCouponConsumption failed, will retry next round", e);
        }
    }
}
