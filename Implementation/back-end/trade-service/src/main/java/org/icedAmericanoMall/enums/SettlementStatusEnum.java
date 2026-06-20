package org.icedAmericanoMall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum SettlementStatusEnum {
    PENDING(1, "待结算"),
    SETTLED(2, "已结算"),
    PAID_OUT(3, "已打款");

    @EnumValue
    private final int code;
    private final String desc;

    SettlementStatusEnum(int code, String desc) { this.code = code; this.desc = desc; }
}
