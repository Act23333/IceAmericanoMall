package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

/**
 * 售后申请状态枚举 —— 对应 {@code after_sale.status}。
 *
 * <pre>
 * 状态流转:
 *   1-PENDING(待审核) → 2-APPROVED(同意) → 4-RETURNED(已退货) → 5-REFUNDED(已退款) → 6-COMPLETED(已完成)
 *                     ↘ 3-REJECTED(拒绝)
 * </pre>
 */
@Getter
public enum AfterSaleStatusEnum {

    PENDING(1, "待审核"),
    APPROVED(2, "同意"),
    REJECTED(3, "拒绝"),
    RETURNED(4, "已退货"),
    REFUNDED(5, "已退款"),
    COMPLETED(6, "已完成");

    private final int code;
    private final String desc;

    AfterSaleStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码
     * @return 匹配的枚举，找不到返回 {@code null}
     */
    public static AfterSaleStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
