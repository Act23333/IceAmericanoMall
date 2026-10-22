package org.icedamericanomall.trade.domain.repository;

import java.time.LocalDateTime;

/**
 * 出款尝试账仓储（端口）。
 *
 * <p>一笔提现可以有**多个**出款尝试：渠道契约要求"失败后换新单据"，所以单号不能复用
 * （`open-items` §7.2 / 渠道事实基线 §10）。
 *
 * <h2>实现方必须守的四条（写在这里，因为违反任何一条都会在并发下破掉不变量）</h2>
 * <ol>
 *   <li><b>占号必须锁提现单行</b>：{@code SELECT ... FOR UPDATE} 锁 {@code withdrawal} 行，
 *       在锁内完成"查非终态 → 取 max(attempt_no)+1 → 插入"。先查后插在并发下不成立
 *       （两个线程同时查到"没有非终态"，各插一行）。
 *       实测：H2 的 {@code FOR UPDATE} **真的阻塞**（见 {@code LockSemanticsH2Test}），所以这条可验。</li>
 *   <li><b>本方法必须是 {@code @Transactional} 且不得自调用</b>：锁只在事务内有效；
 *       同类方法之间调用会绕过代理，事务不生效 ⇒ 锁形同未取。</li>
 *   <li><b>撞 {@code UNIQUE(withdrawal_id, attempt_no)} 直接抛，不在同事务重试</b>：
 *       锁已保证串行，撞键只说明**有代码绕过了锁**，重试会把 bug 藏起来。</li>
 *   <li><b>重试上限收敛在这里</b>：达到上限即拒绝占号（返回/抛出可区分的失败），
 *       让上层停在人工可介入的显式失败，而不是无限重发。</li>
 * </ol>
 */
public interface WithdrawalPayoutAttemptRepository {

    /** 尝试状态。与 {@code withdrawal.payout_no} 派生指针配合：非终态最多一条。 */
    enum AttemptState {
        /** 已发起、结果未回。 */
        REQUESTED,
        /** 结果未知（渠道非终态 / 系统错误 / 撤销中）。 */
        UNKNOWN,
        /** 渠道明确成功。 */
        SUCCESS,
        /** 渠道明确失败（可换号重发，受上限约束）。 */
        FAILED,
        /** 已撤销（主动终止；**不计入失败重试上限**）。 */
        CANCELLED
    }

    /** 一次尝试的视图。 */
    record Attempt(Long id, Long withdrawalId, int attemptNo, String payoutNo, String channel,
                   AttemptState state, String channelRef, LocalDateTime requestedAt,
                   LocalDateTime finishedAt) {
    }

    /**
     * 占号：为提现单创建一条新尝试（**锁提现单行**，见类注释）。
     *
     * <p>**一个事务内完成四件事**：锁提现单行 → 插 attempt → 打标 → 写 {@code withdrawal.payout_no} 派生指针。
     * 四者必须原子：否则出现「有尝试没打标」或「有指针没尝试」。
     * 顺序有意为之：**先插 attempt，再写指针** —— 反过来的话，一旦事务没生效就会留下悬空指针。
     *
     * @param withdrawalId 提现单技术主键
     * @param channel      渠道标识
     * @param factory      单号工厂（锁内以 {@code withdrawalNo + attemptNo} 调用，必须是纯函数）
     * @param now          发起时刻（重试节流与到达判定依据）
     * @return 落库后的尝试（含 {@code attemptNo}）
     * @throws IllegalStateException 已达重试上限，或存在未收敛的非终态尝试
     */
    Attempt claimAndMark(Long withdrawalId, String channel,
                         org.icedamericanomall.trade.domain.port.PayoutNoFactory factory, LocalDateTime now);

    /**
     * 状态写回（给 {@code query()} 的结果一个落点）。
     *
     * <p><b>只改状态，不管资金</b>：资金处置（解冻 / 转已付）由应用层按
     * {@code open-items} §7.2 的分层规则决定 —— 仓储层不碰 {@code frozen_amount} / {@code paid_total}。
     *
     * @param payoutNo 幂等键
     * @param from     期望当前状态（条件更新，0 行即状态已变）
     * @param to       目标状态
     * @return 影响行数；0 表示状态已变（或单号不存在），调用方须区分
     */
    int updateState(String payoutNo, AttemptState from, AttemptState to);

    /** 查当前有效尝试（提现单视角；{@code empty} = 没有进行中的尝试）。 */
    java.util.Optional<Attempt> findActiveAttempt(Long withdrawalId);
}
