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
 *   <li><b>resume 复用 {@link #pay}</b>：超时单的重试与首次受理走同一个方法、同一个 {@code payoutNo}，
 *       「重试不会出两次钱」<b>完全依赖本端口的幂等契约</b>。</li>
 *   <li><b>但 resume 不足以覆盖"已发出、结果未知"</b>（2026-10-19 修正原裁决）：
 *       渠道契约里"未知"是正常状态（微信 {@code ACCEPTED}/{@code PROCESSING}、
 *       支付宝 {@code SYSTEM_ERROR}），官方明确要求**主动查询**。
 *       复用 {@code pay} 只能处理"我方没发出"，处理不了"发出了但不知道结果" ——
 *       所以端口<一次>提供 {@link #query}，它是查询兜底与对账的前置（两方案共同前提）。</li>
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
     * 查询出款结果（**查询兜底与对账的前置**）。
     *
     * <p>为什么必须有：渠道侧"未知"是正常状态（微信 {@code ACCEPTED}/{@code PROCESSING}、
     * 支付宝 {@code SYSTEM_ERROR}），官方要求商户主动查询确认；没有本方法，
     * 「结果未知」的单只能靠人工猜。
     *
     * @param payoutNo 幂等键（与 {@link #pay} 用的是同一个）
     * @return 渠道当前状态；<b>返回 {@link QueryState#UNKNOWN} 表示"查不到/不确定"，不是失败</b>
     */
    PayoutQueryResult query(String payoutNo);

    /** 渠道查询结果的状态（**不是**资金处置结论，映射见 open-items §7.2）。 */
    enum QueryState {
        /** 渠道明确成功，资金已出。 */
        SUCCESS,
        /** 渠道明确失败，资金未出；按契约须**换新单据**重发。 */
        FAILED,
        /**
         * 已撤销/撤销中。
         *
         * <p><b>不等于失败</b>：撤销是主动终止，资金并未"失败出账"，且撤销可能仍在进行中。
         */
        CANCELLED,
        /**
         * 未知：渠道无此单、状态未收敛、或渠道报"系统错误"。
         *
         * <p><b>不等于失败</b>：当失败处理会造成"钱已出、账记失败"。
         * 只能停在付款中等查询或人工。
         */
        UNKNOWN
    }

    /**
     * 查询结果。
     *
     * @param state      渠道状态
     * @param channelRef 渠道流水号（渠道有则带回，用于对账）
     * @param message    渠道返回摘要（截断；不放原始报文）
     */
    record PayoutQueryResult(QueryState state, String channelRef, String message) {
    }

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
