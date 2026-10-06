package org.icedamericanomall.pay.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小程序内拉起支付请求（PAY7，{@code POST /api/pay/wechat/jsapi}）。
 *
 * <p>{@code openid} 是用户在<b>商户下单 appid</b>（{@code wechat.pay.app-id}）下的 openid，
 * 与该 appid 必须同源——微信 JSAPI 下单将 {@code payer.openid} 与 appid 绑定校验，
 * 不同源渠道直接拒绝（同源约束由 {@code wechat.pay.app-id} 配置侧保证，见
 * ia-integration {@code WechatPayProperties} 的说明）。
 */
@Data
public class JsapiPayReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务订单号（trade-service 的 orderNo）。 */
    @NotBlank(message = "orderNo 不能为空")
    private String orderNo;

    /** 用户在下单 appid 下的 openid（小程序场景即小程序 openid）。 */
    @NotBlank(message = "openid 不能为空")
    private String openid;
}
