package org.icedamericanomall.wallet.domain.model;

import lombok.Getter;

/**
 * 钱包流水类型 —— 对应 {@code wallet_transaction.type}。
 *
 * <p>方向由类型表达，金额恒正（{@code amount > 0}）：收入加余额、支出减余额。
 * 收入流水的典型场景是充值（{@code WalletManager.recharge}）与售后退款入账
 * （{@code WalletManager.refund}，F1）。
 */
@Getter
public enum WalletTransactionType {

    INCOME(1, "收入"),
    OUTCOME(2, "支出");

    private final int code;
    private final String desc;

    WalletTransactionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
