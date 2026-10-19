package org.icedamericanomall.trade.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.finance.SettlementSweeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 提现链路上游的<b>触发适配器</b>：调度（XXL-Job）→ {@link SettlementSweeper}。
 *
 * <p><b>为什么必须有它</b>：{@code SettlementManager#settle} 是 PENDING→SETTLED 的唯一入口，
 * 但在 T-WITHDRAW 第一步之前**没有任何调用者** —— 结算单永远停在 PENDING，商家
 * {@code settled_total} 恒为 0，{@code available()} 恒为 0 ⇒ 提现在生产上<b>完全不可用</b>。
 *
 * <p>本类只做四件事：取一次时钟（{@code now} 与 {@code today} 同源）、取批次上限、调 sweeper、
 * 把<b>四个计数 + 耗时</b>写进日志。循环在 {@link SettlementSweeper}（app 层，端点与调度共用的同一份语义）。
 *
 * <p><b>耗时为什么要打</b>：XXL-Job 侧有任务超时；{@code trade.settlement.sweep.limit} 与超时必须匹配 ——
 * 单批 200 条若跑过超时时间，调度器会判超时，而 JVM 仍在跑（下一次调度可能并发执行）。
 * 日志里的耗时是调这两个数字的唯一依据（配不上就别调大 limit）。
 *
 * <p><b>幂等</b>：重复触发、多实例并发触发都只会有一方生效（另一方条件更新 0 行 = 已结算，跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementSettleJob {

    /** 单批上限：调度频次交给 XXL-Job，批次大小在此控制，避免一次扫全表。**须与任务超时匹配**。 */
    @Value("${trade.settlement.sweep.limit:200}")
    private int batchLimit;

    private final SettlementSweeper settlementSweeper;

    @XxlJob("settlePendingSettlements")
    public void settlePendingSettlements() {
        long startedAt = System.currentTimeMillis();
        // 时钟只取一次：候选查询与逐条条件更新共用同一个「今天 / 此刻」
        LocalDateTime now = LocalDateTime.now();
        SettlementSweeper.SweepResult result =
                settlementSweeper.sweep(now.toLocalDate(), now, batchLimit);
        long costMs = System.currentTimeMillis() - startedAt;

        log.info("XXL-Job: settlePendingSettlements 完成 —— 扫描 {}，结算 {}，条件未满足 {}，异常失败 {}，耗时 {}ms"
                        + "（batchLimit={}，耗时接近任务超时请下调 limit）",
                result.scanned(), result.settled(), result.skipped(), result.failed(), costMs, batchLimit);
    }
}
