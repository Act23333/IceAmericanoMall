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

    /**
     * 按库值取枚举（<b>补偿重试的还原口径</b>：{@code invoice_link_compensation.kind} → 该重调哪一条联动）。
     *
     * <p>存在的理由：重试任务只拿到落库的 {@code kind}（{@code TINYINT}），若在调度侧写
     * {@code switch (1)} / {@code if (kind == 2)}，就地把「1=VOID / 2=PARTIAL」这条映射复制出了
     * 第二份真相 —— 将来 {@code kind} 增项时必然漏改一处。映射只留在本枚举。
     *
     * @param code 库值（1=VOID / 2=PARTIAL）
     * @return 对应枚举
     * @throws IllegalArgumentException 未知值（列上有 {@code NOT NULL} 无 {@code CHECK}，
     *                                 脏数据仍可能绕过；抛出后由重试任务按失败计次，最终转永久失败，
     *                                 <b>不</b>静默跳过）
     */
    public static InvoiceLinkKindEnum of(Integer code) {
        if (code != null) {
            for (InvoiceLinkKindEnum kind : values()) {
                if (kind.code == code) {
                    return kind;
                }
            }
        }
        throw new IllegalArgumentException("未知的发票联动补偿类型: " + code);
    }
}
