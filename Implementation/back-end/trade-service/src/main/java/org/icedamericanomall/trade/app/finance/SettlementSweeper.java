package org.icedamericanomall.trade.app.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量结算的<b>批处理外壳</b>（use-case 层）：拿候选 → 逐条经 {@link SettlementManager#settle} 结算。
 *
 * <p><b>为什么单独一个 Bean，而不是写进 {@code SettlementManager}</b>：{@code settle()} 带
 * {@code @Transactional}，靠 Spring 代理生效。若在同一 Bean 内 `settlePending()` 循环调用
 * `this.settle(...)`，那是<b>自调用</b>，代理被绕过 ⇒ 事务不生效（单测全绿、线上无事务）。
 * 放到另一个 Bean 后，调用穿过代理：<b>每条一个事务</b>，单条失败只回滚本条。
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
     * @param today 应用时钟的「今天」（账期口径与 {@code settle()} 一致，不用数据库时钟）
     * @param limit 单批上限（&le; 0 视为不扫描，返回 0 —— 防止调用方传错值把全表扫一遍）
     * @return 真正结算成功的条数（条件未命中或抛异常的都不计入）
     */
    public int sweep(LocalDate today, int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<Long> ids = settlementRepository.findSettleableIds(today, limit);
        int settled = 0;
        for (Long id : ids) {
            try {
                if (settlementManager.settle(id)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                // 单条失败不中断整批：本条已整事务回滚（settled_total 不会半加），下次扫描会再试
                log.warn("结算单结算失败，跳过：id={}", id, e);
            }
        }
        return settled;
    }
}
