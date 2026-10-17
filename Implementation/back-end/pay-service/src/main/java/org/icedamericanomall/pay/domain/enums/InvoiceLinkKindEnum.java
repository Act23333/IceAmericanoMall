package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * 发票联动补偿类型枚举 —— 对应 {@code invoice_link_compensation.kind}。
 *
 * <p><b>为什么需要区分 kind</b>：退款成功后的发票联动有两条互斥分支（{@code PayEventNotifier#triggerInvoiceLinkage}
 * 的 if/else 二分），两条分支的补偿动作<b>不同</b>：
 * <ul>
 *   <li>{@link #VOID}（I56）—— 全额退款 → trade 侧冲红/作废发票。失败即「发票仍是有效已开具状态」，
 *       补偿动作 = 重调 {@code void-on-full-refund}；</li>
 *   <li>{@link #PARTIAL}（I60）—— 部分退款 → trade 侧置 {@code partial_refund_flag=1} 待人工处理。
 *       失败即「该票没有待人工处理的标记」，补偿动作 = 重调 {@code partial-refund-flag}。</li>
 * </ul>
 *
 * <p>两条分支的补偿语义不可互换 —— 故 {@code kind} 与 {@code order_no} / {@code scene_ref} 一起构成
 * 幂等锚点（{@code uk_order_scene_kind}）：同一订单在不同场景下可能出现「先部分退款、后全额退款」，
 * 若只有 {@code (order_no, scene_ref)} 两列，后者会被前者错误地幂等吞掉。
 */
@Getter
public enum InvoiceLinkKindEnum {

    /** 全额退款冲红/作废（I56）—— 退款金额 = 支付单实付全额。 */
    VOID(1, "全额退款冲红/作废"),

    /** 部分退款置标待人工处理（I60）—— 退款金额 &lt; 支付单实付全额。 */
    PARTIAL(2, "部分退款置标待人工处理");

    private final int code;
    private final String desc;

    InvoiceLinkKindEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
