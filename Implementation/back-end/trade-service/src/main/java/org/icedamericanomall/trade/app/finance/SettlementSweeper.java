package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.adapter.response.SettlementSweepVO;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** 响应里最多带多少条失败明细：够运维定位，又不至于把响应撑成日志倾倒口。 */
    private static final int MAX_FAILURES = 20;

    /** 未指定 limit 时的默认单批上限（与调度侧的 {@code trade.settlement.sweep.limit} 默认值保持一致）。 */
    private static final int DEFAULT_LIMIT = 200;

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
        return sweep(today, now, limit, null);
    }

    /**
     * 同上，但可只扫某个账期（{@code periodEnd} 为 null = 不按账期过滤）。
     *
     * <p>为什么要这个参数：运营补跑某一天/某段账期时，全量扫会把不相干的单一起结算；
     * 按账期过滤是把「补跑范围」变成显式输入，而不是靠人去核对 {@code scanned}。
     */
    public SweepResult sweep(LocalDate today, LocalDateTime now, int limit, LocalDate periodEnd) {
        if (limit <= 0) {
            return new SweepResult(0, 0, 0, 0, List.of());
        }
        List<Long> ids = settlementRepository.findSettleableIds(today, limit, periodEnd);
        int settled = 0;
        int skipped = 0;
        int failed = 0;
        List<Failure> failures = new ArrayList<>();
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
                if (failures.size() < MAX_FAILURES) {
                    // 只留前 N 条：响应要给运维**能直接看的失败原因**，又不能变成一个日志倾倒口
                    failures.add(new Failure(id, e.getClass().getSimpleName() + ": " + e.getMessage()));
                }
            }
        }
        return new SweepResult(ids.size(), settled, skipped, failed, List.copyOf(failures));
    }

    /**
     * 管理端触发入口：扫一批并把结果映射成对外 VO（含前若干条失败明细）。
     *
     * <p>放在 app 层而不是 controller 里：controller 只做参数校验 + 调 Manager + 包 {@code Result}
     * （仓库既有约定），映射逻辑跟 {@link #sweep} 挨着才不容易两边口径漂。
     *
     * @param limit     单批上限；null 或 &le; 0 用默认值
     * @param periodEnd 只扫该账期；null = 不按账期过滤（补跑某天时用它把范围变成显式输入）
     */
    public SettlementSweepVO sweepForAdmin(Integer limit, LocalDate periodEnd) {
        LocalDateTime now = LocalDateTime.now();
        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        SweepResult result = sweep(now.toLocalDate(), now, effectiveLimit, periodEnd);

        SettlementSweepVO vo = new SettlementSweepVO();
        vo.setScanned(result.scanned());
        vo.setSettled(result.settled());
        vo.setSkipped(result.skipped());
        vo.setFailed(result.failed());
        vo.setHealthy(result.healthy());
        vo.setFailures(result.failures().stream().map(failure -> {
            SettlementSweepVO.Failure item = new SettlementSweepVO.Failure();
            item.setSettlementId(failure.settlementId());
            item.setReason(failure.reason());
            return item;
        }).toList());
        return vo;
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
    public record SweepResult(int scanned, int settled, int skipped, int failed, List<Failure> failures) {

        /** 是否全部正常（没有异常失败）。运维告警口径：{@code failed > 0} 才值得报警。 */
        public boolean healthy() {
            return failed == 0;
        }
    }

    /**
     * 一条失败明细。
     *
     * <p>为什么要有它：只给 {@code failed} 计数，运维只知道「有 3 条失败」，还得自己去翻日志；
     * 把前 N 条 (结算单 id + 原因) 直接放进响应，才能当场判断是「数据问题」还是「环境问题」。
     *
     * @param settlementId 失败的结算单主键
     * @param reason       异常类型 + 消息（不截断原因，截断的是条数）
     */
    public record Failure(Long settlementId, String reason) {
    }
}
