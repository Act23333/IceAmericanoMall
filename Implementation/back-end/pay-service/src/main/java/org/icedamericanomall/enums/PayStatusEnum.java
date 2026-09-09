package org.icedamericanomall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum PayStatusEnum {
    PENDING_SUBMIT(0, "待提交"),
    PENDING_PAY(1, "待支付"),
    TIMEOUT_CANCEL(2, "超时取消"),
    SUCCESS(3, "成功");

    @EnumValue
    private final int code;
    private final String desc;

    PayStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
