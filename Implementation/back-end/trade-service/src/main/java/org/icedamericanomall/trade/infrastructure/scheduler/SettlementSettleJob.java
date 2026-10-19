package org.icedamericanomall.trade.infrastructure.scheduler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.app.finance.SettlementSweeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 提现链路上游的<b>触发适配器</b>：调度（XXL-Job）→ {@link SettlementSweeper}。
 *
 * <p><b>为什么必须有它</b>：{@code SettlementManager#settle} 是 PENDING→SETTLED 的唯一入口，
 * 但在 T-WITHDRAW 第一步之前**没有任何调用者** —— 结算单永远停在 PENDING，商家
 * {@code settled_total} 恒为 0，{@code available()} 恒为 0 ⇒ 提现在生产上<b>完全不可用</b>。
 *
 * <p>本类**只做三件事**：算「今天」、取批次上限、把结果写进日志。真正的循环在
 * {@link SettlementSweeper}（app 层，可被端点与调度共用的同一份语义）。
 *
 * <p><b>幂等</b>：重复触发、多实例并发触发都只会有一方生效（另一方条件更新 0 行 = 已结算，跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementSettleJob {

    /** 单批上限：调度频次交给 XXL-Job，批次大小在此控制，避免一次扫全表。 */
    @Value("${trade.settlement.sweep.limit:200}")
    private int batchLimit;

    private final SettlementSweeper settlementSweeper;

    @XxlJob("settlePendingSettlements")
    public void settlePendingSettlements() {
        int settled = settlementSweeper.sweep(LocalDate.now(), batchLimit);
        log.info("XXL-Job: settlePendingSettlements 完成，实际结算 {} 条（batchLimit={}）", settled, batchLimit);
    }
}
