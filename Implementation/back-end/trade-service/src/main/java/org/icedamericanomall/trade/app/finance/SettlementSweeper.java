package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量结算的<b>批处理外壳</b>（use-case 层）：拿候选 → 逐条经 {@link SettlementManager#settleAt} 结算。
 *
 * <p><b>为什么单独一个 Bean，而不是写进 {@code SettlementManager}</b>：{@code settle()} 带
 * {@code @Transactional}，靠 Spring 代理生效。若在同一 Bean 内 `settlePending()` 循环调用
 * `this.settle(...)`，那是<b>自调用</b>，代理被绕过 ⇒ 事务不生效（单测全绿、线上无事务）。
 * 放到另一个 Bean 后，调用穿过代理：<b>每条一个事务</b>，单条失败只回滚本条。
 *
 * <p><b>时钟只取一次</b>：候选查询与逐条条件更新共用同一对 {@code today} / {@code now}
 * （跨零点扫描不会一半按 D 判、一半按 D+1 判）。
 *
 * <p>本类<b>不含</b>任何额度判断、不动 {@code frozen_amount}、不动 {@code paid_total} ——
 * 它只是把「已到期」的结算单结算成「可提现」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementSweeper {

    private final SettlementRepository settlementRepository;
    private final SettlementManager settlementManager;

    /**
     * 扫描并结算一批。
     *
     * @param today 应用时钟的「今天」（与 {@code now} 必须同源：{@code now.toLocalDate()}）
     * @param now   应用时钟的「此刻」（候选查询用它算出的 {@code today}，条件更新用它写 update_time）
     * @param limit 单批上限（&le; 0 视为不扫描，返回全 0 —— 防止调用方传错值把全表扫一遍）
     * @return 四个计数，<b>运维据此区分「没有可结算的」与「结算失败」</b>
     */
    public SweepResult sweep(LocalDate today, LocalDateTime now, int limit) {
        if (limit <= 0) {
            return new SweepResult(0, 0, 0, 0);
        }
        List<Long> ids = settlementRepository.findSettleableIds(today, limit);
        int settled = 0;
        int skipped = 0;
        int failed = 0;
        for (Long id : ids) {
            try {
                if (settlementManager.settleAt(id, today, now)) {
                    settled++;
                } else {
                    // 扫描到但条件不满足（两次扫描之间被别处结算/账期回退）—— 正常现象，不是错误
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                // 单条失败不中断整批：本条已整事务回滚（settled_total 不会半加），下次扫描会再试
                log.warn("结算单结算失败，跳过：id={}", id, e);
            }
        }
        return new SweepResult(ids.size(), settled, skipped, failed);
    }

    /**
     * 批处理结果。
     *
     * <p>为什么要有 {@code skipped} 与 {@code failed} 两个字段：只返回一个数字时，
     * 「今天没有可结算的」和「有单但全失败」看起来完全一样 —— 运维无法判断该不该报警。
     *
     * @param scanned 候选条数（{@code = settled + skipped + failed}）
     * @param settled 真的结算成功
     * @param skipped 条件不满足（幂等 no-op：已结算 / 账期未到 / 零额 / 无订单 / 不存在）
     * @param failed  抛异常（真错误：DB 故障、账不平……需要人看日志）
     */
    public record SweepResult(int scanned, int settled, int skipped, int failed) {

        /** 是否全部正常（没有异常失败）。运维告警口径：{@code failed > 0} 才值得报警。 */
        public boolean healthy() {
            return failed == 0;
        }
    }
}
