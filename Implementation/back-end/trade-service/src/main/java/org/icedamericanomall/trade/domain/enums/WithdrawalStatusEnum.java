package org.icedamericanomall.trade.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum WithdrawalStatusEnum {
    PENDING_REVIEW(1, "待审核"),
    PAID_OUT(2, "已打款"),
    REJECTED(3, "已拒绝");

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
