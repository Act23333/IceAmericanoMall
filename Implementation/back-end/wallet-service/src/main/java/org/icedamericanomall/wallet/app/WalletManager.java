package org.icedamericanomall.wallet.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.wallet.domain.model.WalletTransaction;
import org.icedamericanomall.wallet.domain.repository.WalletAccountRepository;
import org.icedamericanomall.wallet.domain.repository.WalletTransactionRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包用例编排器（应用层）。
 *
 * <p>不变式：余额非负。扣减失败（余额不足/无账户）→ {@code BALANCE_INSUFFICIENT}；
 * 入账 upsert 兜底初始化账户行。金额校验为参数合法性，属应用层入参防御；
 * 并发安全由仓储层条件 UPDATE 承担（ADR-005 同构实现）。
 *
 * <p>事务：
 * <ul>
 *   <li>{@link #deductBalance} / {@link #recharge} —— 单语句原子操作，无跨行写入，
 *       <b>不声明</b> {@code @Transactional}；</li>
 *   <li>{@link #refund}（F1 售后退款入账）—— <b>加余额 + 写流水两处写入必须同事务</b>
 *       （不能「钱到了没留痕」或「留痕了钱没到」），故显式声明
 *       {@code @Transactional(rollbackFor = Exception.class)}。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletManager {

    /** 退款流水的固定摘要。 */
    static final String REFUND_REMARK = "售后退款";
    static final String DEDUCT_REMARK = "余额支付";

    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /** 查询余额（无账户行即 0）。 */
    public int getBalance(Long userId) {
        return walletAccountRepository.findBalanceByUserId(userId).orElse(0);
    }

    /**
     * 原子扣减余额（余额支付）；余额不足抛 BALANCE_INSUFFICIENT。
     */
    public void deductBalance(String transactionNo, Long userId, int amount) {
        if (transactionNo == null || transactionNo.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "扣款流水号不能为空（幂等键）");
        }
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "扣减金额必须大于0");
        }
        // ★ 顺序是核心：**先插流水**（uk_transaction_no 拦住重复请求），再动余额。
        // 反过来（先扣余额后插流水）一旦第二次进来，余额已经错了 —— 流水只能事后发现，不能事前阻止。
        try {
            walletTransactionRepository.insert(WalletTransaction.expense(
                    transactionNo, userId, amount, transactionNo, DEDUCT_REMARK));
        } catch (DuplicateKeyException e) {
            // 幂等：同一 transactionNo 重复请求，不重复扣余额
            log.info("[wallet][deduct][idempotent] 流水已存在，幂等返回: transactionNo={}, userId={}, amount={}",
                    transactionNo, userId, amount);
            return;
        }
        if (walletAccountRepository.deduct(userId, amount) <= 0) {
            // 余额不足：抛出让本地事务回滚，流水一并撤销（否则会留下"有流水没扣钱"的假记录）
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足");
        }
        log.info("[wallet][deduct][ok] userId={}, amount={}, transactionNo={}", userId, amount, transactionNo);
    }

    /**
     * 入账（充值/退款）；upsert 兜底初始化账户行。
     */
    public void recharge(Long userId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "充值金额必须大于0");
        }
        walletAccountRepository.credit(userId, amount);
        log.info("[wallet][recharge][ok] userId={}, amount={}", userId, amount);
    }

    /**
     * 售后退款入账（F1 余额渠道原路退回，I41）。
     *
     * <pre>
     * Scenario: 余额渠道退款入账且幂等
     *   Given 退款单号 refundNo、用户 userId、退款金额 amount（分）
     *   When 首次请求
     *   Then wallet_account.balance 自增 amount，且写入一条 type=1(收入)、bizNo=refundNo 的流水（同事务）
     *   When 同一 transactionNo 重复请求
     *   Then 幂等成功返回，余额不再增加、流水不重复（先查 + uk_transaction_no 兜底）
     * </pre>
     *
     * @param transactionNo 流水号（= 退款单号 refundNo，<b>幂等键</b>）
     * @param userId        入账用户
     * @param amount        入账金额（分，{@code > 0}）
     * @param bizNo         业务关联号（退款单号；为空时回落 {@code transactionNo}）
     * @throws BadRequestException 参数非法（金额 ≤ 0 / 流水号为空）
     * @throws BizException        并发重复入账（唯一键兜底命中）——本次加余额已回滚，调用方可安全重试
     */
    @Transactional(rollbackFor = Exception.class)
    public void refund(String transactionNo, Long userId, int amount, String bizNo) {
        if (transactionNo == null || transactionNo.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "流水号不能为空");
        }
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "退款金额必须大于0");
        }
        if (walletTransactionRepository.existsByTransactionNo(transactionNo)) {
            log.info("[wallet][refund][idempotent] 流水已存在，幂等返回: transactionNo={}, userId={}, amount={}",
                    transactionNo, userId, amount);
            return;
        }
        walletAccountRepository.credit(userId, amount);
        try {
            walletTransactionRepository.insert(WalletTransaction.income(
                    transactionNo, userId, amount, bizNo == null ? transactionNo : bizNo, REFUND_REMARK));
        } catch (DuplicateKeyException e) {
            // 并发孪生请求已入账：抛出让本地事务整体回滚（本次加余额一并撤销），保证不重复加余额。
            // 调用方（pay-service）会把退款单置失败，管理端重试时命中上面的幂等分支 → 最终一致收敛。
            log.warn("[wallet][refund][conflict] 并发重复入账，回滚本次加余额: transactionNo={}", transactionNo);
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "重复入账请求，请重试");
        }
        log.info("[wallet][refund][ok] userId={}, amount={}, transactionNo={}, bizNo={}",
                userId, amount, transactionNo, bizNo);
    }
}
