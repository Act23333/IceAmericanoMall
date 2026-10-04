package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

/**
 * 对账渠道枚举 —— 对应 {@code reconciliation_batch.channel}（F4 渠道对账）。
 *
 * <p>与 {@link PayChannelEnum} 的区别：对账只覆盖<b>外部渠道</b>（{@code WECHAT} / {@code ALIPAY}）——
 * 余额渠道是本地闭环，不存在外部账单可对，故不参与对账。两者的渠道码取值保持一致，
 * 以便与 {@code pay_order.pay_channel_code} 直接比对。
 */
@Getter
public enum ReconciliationChannelEnum {

    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝");

    private final String code;
    private final String desc;

    ReconciliationChannelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按渠道码解析（大小写不敏感）。
     *
     * @param code 渠道码（可空 → 返回 {@code null}，表示"未指定渠道"）
     * @return 匹配的枚举；未指定返回 {@code null}；非法取值抛 {@code PARAM_ERROR}
     * @throws BadRequestException 渠道码非法（非空但不匹配任何渠道）
     */
    public static ReconciliationChannelEnum of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ReconciliationChannelEnum c : values()) {
            if (c.code.equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        throw new BadRequestException(ErrorCode.PARAM_ERROR, "不支持的对账渠道: " + code);
    }
}
