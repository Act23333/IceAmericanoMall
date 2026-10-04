package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * 退款单状态枚举 —— 对应 {@code refund_order.status}（F1 买家退款资金流）。
 *
 * <pre>
 * 状态流转:
 *   0-PROCESSING(处理中) → 1-SUCCESS(成功)   [余额/支付宝同步成功，或微信回调确认]
 *                        ↘ 2-FAILED(失败)    [渠道拒绝/调用失败；管理端可重试]
 *   2-FAILED ──重试(AD43)──→ 0-PROCESSING
 * </pre>
 *
 * <p><b>与售后单状态的关系</b>：退款<b>处理中/失败</b>时售后单停留 {@code APPROVED(2)}；
 * 仅退款单成功后经 I42 把售后单迁移到 {@code REFUNDED(5)}。
 */
@Getter
public enum RefundStatusEnum {

    PROCESSING(0, "处理中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    RefundStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
