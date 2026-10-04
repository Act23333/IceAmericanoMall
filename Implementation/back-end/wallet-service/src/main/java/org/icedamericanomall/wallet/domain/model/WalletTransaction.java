package org.icedamericanomall.wallet.domain.model;

/**
 * 钱包流水（领域模型，落 {@code wallet_transaction} 表）—— 余额每笔变动的留痕。
 *
 * <p><b>幂等键</b>：{@link #transactionNo()}（业务唯一键，UUID）。退款入账时等于
 * {@code refund_order.refund_no}，故「同一退款只入账一次」由本键的唯一约束兜底。
 *
 * <p>用 {@code record} 表达不可变值对象：流水一旦落库即事实数据，不提供修改语义
 * （无 {@code update} 方法、仓储不暴露按技术主键更新）。
 *
 * @param transactionNo 流水号（幂等键）
 * @param userId        用户 ID
 * @param type          变动方向（{@link WalletTransactionType#getCode()}）
 * @param amount        变动金额（分，恒正）
 * @param bizNo         业务关联号（退款流水 = 退款单号；支出流水 = 订单号等）
 * @param remark        摘要（可空）
 */
public record WalletTransaction(String transactionNo, Long userId, int type, int amount, String bizNo,
                                String remark) {

    /** 方向取值为 {@link WalletTransactionType} 的码值（避免领域对象直接持有枚举造成持久化耦合）。 */
    public static WalletTransaction income(String transactionNo, Long userId, int amount, String bizNo,
                                           String remark) {
        return new WalletTransaction(transactionNo, userId, WalletTransactionType.INCOME.getCode(), amount,
                bizNo, remark);
    }
}
