package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.ChannelBillItem;
import org.icedamericanomall.integration.payment.ChannelRefundQueryResult;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.integration.payment.JsapiPayResult;
import org.icedamericanomall.integration.payment.PaymentClient;
import org.icedamericanomall.integration.payment.RefundReason;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.integration.payment.WechatRefundCallbackResult;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付渠道路由器 —— 封装 PaymentClient(WeChat) + AlipayPaymentClient + UserClient(Balance)，
 * 使 PayManager 由 6 依赖降至 4。
 *
 * <p><b>寻址口径</b>：本类所有「对渠道」的参数一律是<b>对外渠道号</b>
 * （{@code pay_order.channel_trade_no} / {@code refund_order.out_refund_no}），
 * 不接受也不透出内部 {@code order_no} / {@code pay_order_no} / {@code refund_no}。
 *
 * <pre>
 * initiate：按渠道调对应客户端生成支付链接（入参为对外渠道号）；BALANCE 不在此方法
 * initiateWechatJsapi：微信 JSAPI 下单（小程序内拉起，PAY7），返回带签名拉起参数集
 * parseWechatCallback：微信回调整包解析（验签 + 解密 + 提取，见 ADR-033）
 * parseWechatRefundCallback：微信退款回调整包解析（F1，复用同一验签/解密基建）
 * refund：按渠道发起退款（对外渠道号 + 对外退款项号；BALANCE 不在此路由）
 * queryWechatRefund：微信退款单查询（按对外退款项号，悬挂退款补偿，ADR-035 风险收敛）
 * downloadWechatBill / downloadAlipayBill：渠道账单下载（F4）
 * verifyAlipayCallback：支付宝回调验签（表单参数口径）
 * </pre>
 */

@Component
@RequiredArgsConstructor
public class PaymentChannelRouter {

    private final PaymentClient wechatClient;
    private final AlipayPaymentClient alipayClient;
    private final BalanceClient balanceClient;

    /**
     * 微信/支付宝：返回支付链接；余额不在此路由（扣款在 PayManager 直接处理）。
     *
     * @param channelTradeNo 对外渠道交易号（渠道 {@code out_trade_no}）
     */
    public String initiatePayment(String channelTradeNo, int amount, String description, PayChannelEnum channel) {
        return switch (channel) {
            case WECHAT -> wechatClient.initiatePayment(channelTradeNo, amount, description);
            case ALIPAY -> alipayClient.initiatePayment(channelTradeNo, amount, description);
            case BALANCE -> throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "余额支付请走 PayManager.payByBalance 扣款路径");
        };
    }

    /**
     * 微信 JSAPI 下单（小程序内拉起支付，PAY7）：appid/openid 同源绑定由适配层配置保证。
     *
     * @param channelTradeNo 对外渠道交易号（微信 {@code out_trade_no}）
     * @param amount         支付金额（分）
     * @param description    商品描述
     * @param openid         用户在下单 appid 下的 openid
     * @return 带签名的小程序拉起参数集（appId/timeStamp/nonceStr/packageVal/signType/paySign）
     * @throws BizException 渠道拒绝 / 调用失败
     */
    public JsapiPayResult initiateWechatJsapi(String channelTradeNo, int amount, String description, String openid) {
        return wechatClient.initiateJsapiPayment(channelTradeNo, amount, description, openid);
    }

    /** 余额扣款（代理 BalanceClient）。 */
    public void deductBalance(Long userId, int amount) {
        balanceClient.deductBalance(userId, amount);
    }

    /**
     * 微信回调整包解析：验签 + AES-GCM 解密 resource + 提取强类型结果，全部在通道适配层完成。
     *
     * @param headers HTTP 请求头（微信签名头）
     * @param rawBody 回调原始报文
     * @return 强类型解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / 报文非法 / 解密失败
     */
    public WechatCallbackResult parseWechatCallback(Map<String, String> headers, String rawBody) {
        return wechatClient.parseWechatCallback(headers, rawBody);
    }

    /** 支付宝回调验签（表单参数口径）。 */
    public boolean verifyAlipayCallback(Map<String, String> params) {
        return alipayClient.verifyCallback(params);
    }

    /**
     * 微信<b>退款</b>回调整包解析（验签 + 解密 + 提取退款字段，F1 / PAY6）。
     *
     * @param headers HTTP 请求头（微信签名头）
     * @param rawBody 回调原始报文
     * @return 强类型退款解析结果
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / 报文非法 / 解密失败 / 缺 out_refund_no
     */
    public WechatRefundCallbackResult parseWechatRefundCallback(Map<String, String> headers, String rawBody) {
        return wechatClient.parseWechatRefundCallback(headers, rawBody);
    }

    /**
     * 按渠道发起退款（F1 原路退回）。余额渠道不在此路由（由 RefundManager 走 I41 余额入账路径）。
     *
     * <p><b>{@code reason} 消毒（ADR-063 §决策 7 表 #5，批次 B7b）</b>：退款原因是<b>人读摘要</b>，
     * 会随渠道账单与工单外泄，因此<b>不得携带任何单号</b>。本方法是全链路唯一的渠道出口，
     * 故消毒也收在这里（{@link RefundReason#channelText(String, String...)}）：
     * 文本含本次的 {@code channel_trade_no} / {@code out_refund_no}、或含任意 32-hex / 20 位渠道号
     * → 整句回落枚举文案 {@code "退款"}；否则按 <b>code point</b> 截断到
     * {@link PaymentClient#MAX_REFUND_REASON_LENGTH}（80，取微信/支付宝较严者）。
     *
     * @param channel      支付渠道
     * @param channelTradeNo 原支付单的<b>对外渠道交易号</b>（渠道用它定位原交易，不是 {@code pay_order_no}）
     * @param outRefundNo  对外退款项号（渠道幂等键，重试复用同号；不是内部 {@code refund_no}）
     * @param refundAmount 本次退款金额（分）
     * @param orderAmount  原支付单金额（分，渠道协议要求）
     * @param reason       退款原因摘要（可空；本方法消毒后再外发）
     * @return 渠道受理结果
     * @throws BizException 渠道拒绝 / 调用失败
     */
    public ChannelRefundResult refund(PayChannelEnum channel, String channelTradeNo, String outRefundNo,
                                      int refundAmount, int orderAmount, String reason) {
        String safeReason = RefundReason.channelText(reason, channelTradeNo, outRefundNo);
        return switch (channel) {
            case WECHAT -> wechatClient.refund(channelTradeNo, outRefundNo, refundAmount, orderAmount, safeReason);
            case ALIPAY -> alipayClient.refund(channelTradeNo, outRefundNo, refundAmount, orderAmount, safeReason);
            case BALANCE -> throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "余额退款请走 I41 余额入账路径");
        };
    }

    /**
     * 微信退款单查询（悬挂退款补偿任务用，ADR-035 风险收敛）—— 与 PAY6 回调互为冗余的终态确认。
     *
     * @param outRefundNo 对外退款项号（微信 {@code out_refund_no}）
     * @return 强类型查询结果（渠道退款单号 + 归一化状态，不暴露微信原始状态串）
     * @throws BizException 查询调用失败（原异常纪律透出，调用方决定是否留待下轮）
     */
    public ChannelRefundQueryResult queryWechatRefund(String outRefundNo) {
        return wechatClient.queryRefund(outRefundNo);
    }

    /** 下载微信渠道账单（F4）。 */
    public List<ChannelBillItem> downloadWechatBill(String billDate) {
        return wechatClient.billDownload(billDate);
    }

    /** 下载支付宝渠道账单（F4）。 */
    public List<ChannelBillItem> downloadAlipayBill(String billDate) {
        return alipayClient.billDownload(billDate);
    }
}
