package org.icedamericanomall.integration.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = WechatPayProperties.PREFIX)
public class WechatPayProperties {

    /** 配置前缀（装配条件判定与绑定共用，避免字面量散落）。 */
    public static final String PREFIX = "wechat.pay";

    private String merchantId;
    /**
     * 商户下单 appid（{@code wechat.pay.app-id}）。
     *
     * <p><b>必须与 openid 同源</b>：微信 v3 下单报文必填 {@code appid}（V5.18 修复——此前
     * Native 下单漏传导致真实环境必然失败）；JSAPI 下单（PAY7）时 {@code payer.openid} 还必须是
     * 用户在<b>本 appid</b> 下的 openid，appid 与 openid 不同源渠道直接拒绝。
     * 小程序支付场景下本字段配置为小程序 appid（与 {@code wechat.mini.app-id} 一致）。
     */
    private String appId;
    private String privateKeyPath;
    private String merchantSerialNumber;
    private String apiV3Key;
    /** 支付结果通知地址（微信 v3：{@code /api/pay/callback/wechat}）。 */
    private String notifyUrl;
    /**
     * 退款结果通知地址（微信 v3：{@code /api/pay/callback/wechat-refund}）。
     *
     * <p>与 {@link #notifyUrl} 分离：微信 v3 的支付通知与退款通知是两个不同的 {@code notify_url}，
     * 回调报文域也不同（{@code trade_state} vs {@code refund_status}），混合会导致退款通知打错端点。
     */
    private String refundNotifyUrl;
}
