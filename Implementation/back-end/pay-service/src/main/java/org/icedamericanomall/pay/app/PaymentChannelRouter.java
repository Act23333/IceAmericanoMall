package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.ChannelBillItem;
import org.icedamericanomall.integration.payment.ChannelRefundQueryResult;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.integration.payment.PaymentClient;
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
 * <pre>
 * initiate：按渠道调对应客户端生成支付链接；BALANCE 不在此方法（扣款由 PayManager 直接处理）
 * parseWechatCallback：微信回调整包解析（验签 + 解密 + 提取，见 ADR-033）
 * parseWechatRefundCallback：微信退款回调整包解析（F1，复用同一验签/解密基建）
 * refund：按渠道发起退款（BALANCE 不在此路由）
 * queryWechatRefund：微信退款单查询（悬挂退款补偿，ADR-035 风险收敛）
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

    /** 微信/支付宝：返回支付链接；余额不在此路由（扣款在 PayManager 直接处理）。 */
    public String initiatePayment(String orderNo, int amount, String description, PayChannelEnum channel) {
        return switch (channel) {
            case WECHAT -> wechatClient.initiatePayment(orderNo, amount, description);
            case ALIPAY -> alipayClient.initiatePayment(orderNo, amount, description);
            case BALANCE -> throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "余额支付请走 PayManager.payByBalance 扣款路径");
        };
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
     * @param channel      支付渠道
     * @param payOrderNo   原支付单号
     * @param refundNo     退款单号（渠道幂等键）
     * @param refundAmount 本次退款金额（分）
     * @param orderAmount  原支付单金额（分，渠道协议要求）
     * @param reason       退款原因摘要（可空）
     * @return 渠道受理结果
     * @throws BizException 渠道拒绝 / 调用失败
     */
    public ChannelRefundResult refund(PayChannelEnum channel, String payOrderNo, String refundNo,
                                      int refundAmount, int orderAmount, String reason) {
        return switch (channel) {
            case WECHAT -> wechatClient.refund(payOrderNo, refundNo, refundAmount, orderAmount, reason);
            case ALIPAY -> alipayClient.refund(payOrderNo, refundNo, refundAmount, orderAmount, reason);
            case BALANCE -> throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "余额退款请走 I41 余额入账路径");
        };
    }

    /**
     * 微信退款单查询（悬挂退款补偿任务用，ADR-035 风险收敛）—— 与 PAY6 回调互为冗余的终态确认。
     *
     * @param refundNo 商户退款单号（微信 {@code out_refund_no}）
     * @return 强类型查询结果（渠道退款单号 + 归一化状态，不暴露微信原始状态串）
     * @throws BizException 查询调用失败（原异常纪律透出，调用方决定是否留待下轮）
     */
    public ChannelRefundQueryResult queryWechatRefund(String refundNo) {
        return wechatClient.queryRefund(refundNo);
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
