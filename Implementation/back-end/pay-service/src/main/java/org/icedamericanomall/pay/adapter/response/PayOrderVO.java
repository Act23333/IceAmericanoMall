package org.icedamericanomall.pay.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付单视图对象 —— 对外返回，屏蔽内部字段（{@code expandJson} / {@code resultCode} /
 * {@code resultMsg} / 技术主键 {@code id} / 审计字段 {@code createTime}/{@code updateTime}）。
 *
 * <p><b>V5.3 字段修剪</b>：移除 {@code bizUserId} —— 支付单恒由当前登录用户发起
 * （userId 取自 {@code UserContext}），回显该字段既冗余又无谓扩散用户身份；
 * 前端支付页只消费 {@code status} 与 {@code qrCodeUrl}（二维码轮询），
 * 其余业务字段（渠道、金额、方式、失效时间、支付成功时间）保留供收银台展示。
 */
@Data
public class PayOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付单号（对外唯一标识）。 */
    private String payOrderNo;

    /** 业务订单号（trade-service 的 orderNo）。 */
    private String bizOrderNo;

    /** 支付渠道：WECHAT / ALIPAY / BALANCE。 */
    private String payChannelCode;

    /** 支付金额（分）。 */
    private Integer amount;

    /** 支付方式（随渠道派生：NATIVE / BALANCE）。 */
    private Integer payType;

    /** 支付状态：1-待支付 2-超时取消 3-成功。 */
    private Integer status;

    /** 支付链接（余额支付为 {@code null}）。 */
    private String qrCodeUrl;

    /** 支付超时时间（收银台倒计时）。 */
    private LocalDateTime payOverTime;

    /** 支付成功时间。 */
    private LocalDateTime paySuccessTime;
}
