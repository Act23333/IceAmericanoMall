package org.icedamericanomall.integration.payment;

/**
 * 渠道退款单状态（查询口径）—— 渠道原始退款状态在本适配层归一化后的三值结果。
 *
 * <p><b>为什么归一化</b>：微信 {@code refund_status} 有 SUCCESS / CLOSED / ABNORMAL / PROCESSING
 * 四种取值，属于渠道协议知识；pay-service 只需要「成功 / 终止失败 / 仍在处理」三值语义来
 * 驱动悬挂退款补偿（ADR-035 风险收敛）。渠道原始状态串不越过适配层边界（与 ADR-033 回调口径一致）。
 *
 * <ul>
 *   <li>{@code SUCCESS} —— 渠道退款成功（微信 {@code refund_status=SUCCESS}）</li>
 *   <li>{@code FAILED} —— 渠道退款终止失败（微信 CLOSED / ABNORMAL），本地可置失败走管理端重试</li>
 *   <li>{@code PROCESSING} —— 渠道仍在处理（微信 PROCESSING），本轮跳过、下轮再查</li>
 * </ul>
 */
public enum ChannelRefundState {

    /** 渠道退款成功。 */
    SUCCESS,

    /** 渠道退款终止失败（微信 CLOSED / ABNORMAL）。 */
    FAILED,

    /** 渠道仍在处理中。 */
    PROCESSING
}
