package org.icedamericanomall.pay.support;

import org.icedamericanomall.integration.payment.ChannelRefundQueryResult;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.integration.payment.ChannelRefundState;
import org.icedamericanomall.integration.payment.JsapiPayResult;
import org.icedamericanomall.integration.payment.PaymentClient;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.integration.payment.WechatRefundCallbackResult;

import java.util.List;
import java.util.Map;

/**
 * {@link PaymentClient} 的记录型测试替身 —— 记录「我方到底把什么号发给了渠道」，
 * 并可按预设回放渠道回调报文。
 *
 * <p>断言点：{@code lastInitiateOutTradeNo} / {@code lastRefundOutTradeNo} /
 * {@code lastRefundOutRefundNo} 三个字段即「渠道视角看到的商户单号」。
 * 资金链路的正确口径是：它们必须是<b>对外渠道号</b>（{@code channel_trade_no} /
 * {@code out_refund_no}），而不是内部 {@code order_no} / {@code pay_order_no} / {@code refund_no}。
 */
public class RecordingPaymentClient implements PaymentClient {

    /** 最近一次 Native 下单收到的 {@code out_trade_no}。 */
    public String lastInitiateOutTradeNo;
    /** 最近一次 JSAPI 下单收到的 {@code out_trade_no}。 */
    public String lastJsapiOutTradeNo;
    /** 最近一次退款收到的 {@code out_trade_no}（原交易渠道号）。 */
    public String lastRefundOutTradeNo;
    /** 最近一次退款收到的 {@code out_refund_no}（对外退款项号）。 */
    public String lastRefundOutRefundNo;
    /** 最近一次退款查询收到的 {@code out_refund_no}。 */
    public String lastQueryOutRefundNo;

    /** 支付回调回放：携带的 {@code out_trade_no}。 */
    public String callbackOutTradeNo;
    public boolean callbackSuccess = true;
    public String callbackRawState = "SUCCESS";

    /** 退款回调回放：携带的 {@code out_refund_no} 与 {@code refund_id}。 */
    public String refundCallbackOutRefundNo;
    public boolean refundCallbackSuccess = true;
    public String refundCallbackRefundId = "wx-refund-id-1";

    /** 退款受理结果（默认「处理中」，与微信真实异步口径一致）。 */
    public ChannelRefundResult refundResult = ChannelRefundResult.processing("wx-refund-id-1");

    @Override
    public String initiatePayment(String outTradeNo, int amount, String description) {
        this.lastInitiateOutTradeNo = outTradeNo;
        return "https://mock-pay.example.com/qr?outTradeNo=" + outTradeNo;
    }

    @Override
    public JsapiPayResult initiateJsapiPayment(String outTradeNo, int amount, String description, String openid) {
        this.lastJsapiOutTradeNo = outTradeNo;
        return new JsapiPayResult("wx-app", "1760000000", "nonce", "prepay_id=" + outTradeNo, "RSA", "sign");
    }

    @Override
    public WechatCallbackResult parseWechatCallback(Map<String, String> headers, String rawBody) {
        return new WechatCallbackResult(callbackOutTradeNo, callbackSuccess, "wx-txn-1", callbackRawState);
    }

    @Override
    public WechatRefundCallbackResult parseWechatRefundCallback(Map<String, String> headers, String rawBody) {
        return new WechatRefundCallbackResult(refundCallbackOutRefundNo, refundCallbackSuccess,
                refundCallbackRefundId, "SUCCESS");
    }

    @Override
    public ChannelRefundResult refund(String outTradeNo, String outRefundNo, int refundAmount, int orderAmount,
                                      String reason) {
        this.lastRefundOutTradeNo = outTradeNo;
        this.lastRefundOutRefundNo = outRefundNo;
        return refundResult;
    }

    @Override
    public ChannelRefundQueryResult queryRefund(String outRefundNo) {
        this.lastQueryOutRefundNo = outRefundNo;
        return new ChannelRefundQueryResult("wx-refund-id-1", ChannelRefundState.PROCESSING);
    }

    @Override
    public List<org.icedamericanomall.integration.payment.ChannelBillItem> billDownload(String billDate) {
        return List.of();
    }

    @Override
    public String queryStatus(String outTradeNo) {
        return "SUCCESS";
    }
}
