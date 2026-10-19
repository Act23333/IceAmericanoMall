package org.icedamericanomall.trade.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum WithdrawalStatusEnum {
    PENDING_REVIEW(1, "待审核"),
    PAID_OUT(2, "已打款"),
    REJECTED(3, "已拒绝"),
    /**
     * 付款中：已受理出款、**尚未拿到渠道回执**。
     *
     * <p>关键语义：「结果未知」不是「失败」。查不到回执就只能停在这里 + 告警，
     * 绝不能因为"没查到"就判失败（否则重试会出两次钱，或释放额度后钱其实已出）。
     * 它**仍然占用 {@code frozen_amount}** —— E3 必须把它算进去。
     */
    PAYING(4, "付款中");

    @EnumValue
    private final int code;
    private final String desc;

    WithdrawalStatusEnum(int code, String desc) { this.code = code; this.desc = desc; }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码
     * @return 匹配的枚举，找不到返回 {@code null}
     */
    public static WithdrawalStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (WithdrawalStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
