package org.icedamericanomall.trade.domain.repository;

import org.icedamericanomall.trade.domain.model.finance.SellerFundAccount;

import java.util.List;

/**
 * 商家资金账户仓储（领域端口）—— 实现方：{@code infrastructure.persistence.finance.SellerFundAccountRepositoryImpl}。
 *
 * <p><b>只有两个写入者</b>（T-WITHDRAW 裁决：账户是权威，别的路径一律不许改它）：
 * <ol>
 *   <li>{@link #addSettled} —— 只由 {@code SettlementManager#settle}（PENDING→SETTLED 的唯一入口）同事务调用；</li>
 *   <li>{@link #addPaid} —— 只由打款（管理端审核为「已打款」）同事务调用。</li>
 * </ol>
 * 两个方法都用「条件更新 + 影响行数」表达约束，不做「先查再改」（那正是要避免的 check-then-act 竞态）。
 */
public interface SellerFundAccountRepository {

    /** 按商家取资金账户（不存在返回 null）。 */
    SellerFundAccount findBySellerId(Long sellerId);

    /**
     * 累计已结算 += {@code amount}（账户行不存在则建行）。
     *
     * <p>必须与 {@code settlement.status} 的条件迁移<b>同事务</b>：状态变了而账户没加 ⇒ 钱凭空消失；
     * 账户加了而状态没变 ⇒ 同一笔结算会被反复累加。
     */
    void addSettled(Long sellerId, int amount);

    /**
     * 累计已打款 += {@code amount}（账户行不存在则建行）。
     *
     * <p><b>不做额度条件</b>：额度校验属<b>申请侧</b>（{@code applyWithdrawal}，第二步改成条件更新预占
     * {@code frozen_amount}）；审核侧只做「状态迁移 + 记账」。
     * 若把额度条件放到这里，会出现「申请时通过、打款时因期间余额变化被拒，而钱其实已经打出去」的假拒绝。
     *
     * <p>本方法不返回成败：唯一调用方（审核为已打款）已在条件迁移里判定过「这笔单还能不能动」。
     */
    void addPaid(Long sellerId, int amount);

    /**
     * 全部账户行 —— <b>仅供对账/自检</b>（第一步验收的三条自检要逐行比对）。
     * 不做业务查询用途（那会把「全量扫账户」当成常规读法）。
     */
    List<SellerFundAccount> findAll();
}
