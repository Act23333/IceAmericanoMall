package org.icedAmericanoMall.enums;

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
}
