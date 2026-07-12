package org.icedAmericanoMall.enums;

import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BadRequestException;

/**
 * 支付渠道枚举 —— 对应 pay_order.pay_channel_code。
 */
@Getter
public enum PayChannelEnum {
    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    BALANCE("BALANCE", "余额支付");

    private final String code;
    private final String desc;

    PayChannelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PayChannelEnum of(String code) {
        if (code != null) {
            for (PayChannelEnum c : values()) {
                if (c.code.equalsIgnoreCase(code)) return c;
            }
        }
        throw new BadRequestException(ErrorCode.PARAM_ERROR, "不支持的支付渠道: " + code);
    }
}
