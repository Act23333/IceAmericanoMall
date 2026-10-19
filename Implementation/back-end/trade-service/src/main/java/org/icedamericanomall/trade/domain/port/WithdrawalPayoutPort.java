package org.icedamericanomall.trade.domain.port;

/**
 * 出款端口（<b>唯一</b>出款出口）。
 *
 * <p><b>为什么现在就要这个端口（哪怕当前只有一个"人工确认"适配器）</b>：
 * 「审核为已打款」这个动作迟早要接真实渠道（微信商家转账 / 银行批量代付）。若 Manager 里写死
 * `paid_total += amount`，接渠道时就要动 review 的骨架与全部用例；先留端口，则是**换一个适配器**。
 *
 * <p><b>实现方的硬约束（写在这里，因为它决定适配器形态）</b>：
 * <ol>
 *   <li>真实渠道 = <b>远程调用</b> ⇒ 不能出现在数据库事务里。接渠道时本端口必须改成
 *       <b>两阶段</b>：Manager 只把单子推进到 {@code PAYING}（短事务），由任务/回调再调渠道并回写。
 *       详见 {@code open-items} §1.35。</li>
 *   <li>幂等由调用方传 {@code payoutNo} 保证：同一 {@code payoutNo} 重复受理必须返回同一结果，
 *       <b>不得重复出款</b>。渠道侧不支持幂等时，适配器自己落去重记录。</li>
 * </ol>
 *
 * <p>当前的 {@code ManualPayoutAdapter} 例外：它是**本地确认**（人已在系统外转账），
 * 不做任何 I/O，所以可以在事务内调用 —— 注释已写明"接真渠道必须换两阶段"。
 */
public interface WithdrawalPayoutPort {

    /**
     * 发起出款。
     *
     * @param command 出款指令（含幂等键）
     * @return 受理结果；<b>受理 ≠ 到账</b>，到账以渠道回执/对账为准
     */
    PayoutResult pay(PayoutCommand command);

    /**
     * 出款指令。
     *
     * @param payoutNo    幂等键（渠道去重依据）：同一提现单只生成一次
     * @param withdrawalId 提现单技术主键（排障用，不进渠道报文）
     * @param sellerId     商家 ID
     * @param amount       金额（分）
     * @param bankAccount  收款账户（渠道必填；人工确认时仅用于核对）
     * @param bankName     开户行
     */
    record PayoutCommand(String payoutNo, Long withdrawalId, Long sellerId, int amount,
                         String bankAccount, String bankName) {
    }

    /**
     * 受理结果。
     *
     * @param accepted   渠道是否受理（false ⇒ 调用方不得把单子标成已打款）
     * @param channelRef 渠道流水号（对账用；人工确认为 {@code MANUAL}）
     * @param reason     拒绝原因分类（供展示，不放原始报文）
     */
    record PayoutResult(boolean accepted, String channelRef, String reason) {
    }
}
