package org.icedAmericanoMall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 支付方式枚举 —— 替代硬编码 payType 数值。
 */
@Getter
public enum PayTypeEnum {
    H5(1, "H5支付"),
    JSAPI(2, "公众号支付"),
    MINI(3, "小程序支付"),
    NATIVE(4, "扫码支付"),
    BALANCE(5, "余额支付");

    @EnumValue
    private final int code;
    private final String desc;

    PayTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
