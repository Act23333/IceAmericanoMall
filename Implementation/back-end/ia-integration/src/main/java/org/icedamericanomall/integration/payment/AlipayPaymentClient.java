package org.icedamericanomall.integration.payment;

import java.util.List;
import java.util.Map;

/**
 * 支付宝支付客户端 —— 与 {@link PaymentClient}（微信）平行的独立接口，避免 Bean 歧义。
 * 当前仅提供 {@link MockAlipayPaymentClient}（虚拟支付链接）；接入支付宝开放平台后追加真实实现即可。
 *
 * <p><b>回调口径</b>：支付宝异步通知走 <b>HTTP 表单参数</b>（与微信 v3 的「裸 JSON + resource 解密」
 * 完全不同），业务字段在参数中直接可见，故其协议键（{@link #PARAM_OUT_TRADE_NO} /
 * {@link #PARAM_TRADE_STATUS}）作为常量收在本接口，避免调用方散落魔法串。
 *
 * <p><b>退款与对账（F1 / F4）</b>：{@link #refund} / {@link #billDownload} 与微信侧同形
 * （共用 {@link ChannelRefundResult} / {@link ChannelBillItem} 出参契约），
 * 使 pay-service 的渠道路由对两个渠道的编排逻辑完全一致。
 */
public interface AlipayPaymentClient {

    /** 异步通知参数键：商户订单号（<b>对外渠道交易号</b>口径，即 {@code pay_order.channel_trade_no}）。 */
    String PARAM_OUT_TRADE_NO = "out_trade_no";

    /** 异步通知参数键：交易状态。 */
    String PARAM_TRADE_STATUS = "trade_status";

    /**
     * 发起支付，返回支付链接（PC 网页/二维码）。
     *
     * @param outTradeNo 对外渠道交易号（支付宝 {@code out_trade_no}，非内部业务单号）
     */
    String initiatePayment(String outTradeNo, int amount, String description);

    /** 验证支付回调签名。 */
    boolean verifyCallback(Map<String, String> params);

    /**
     * 发起支付宝退款（F1，原路退回）。
     *
     * <p>契约与微信侧同形（见 {@code PaymentClient#refund}）：{@code refundAmount} 为本次退款额，
     * {@code orderAmount} 为原支付单金额。当前为 Mock 实现（同步成功）。
     *
     * @param outTradeNo  原支付单的对外渠道交易号
     * @param outRefundNo 对外退款项号（渠道幂等键）
     * @return 渠道受理结果（Mock 为 {@code settled=true}）
     * @throws org.icedamericanomall.common.exception.BizException 渠道拒绝 / 调用失败
     */
    ChannelRefundResult refund(String outTradeNo, String outRefundNo, int refundAmount, int orderAmount,
                               String reason);

    /**
     * 下载并解析支付宝对账账单（F4）。金额统一为<b>分</b>。
     *
     * @param billDate 账单日（{@code yyyy-MM-dd}）
     * @return 账单条目；账单缺失去 / 下载失败时返回空列表并告警，不抛异常
     */
    List<ChannelBillItem> billDownload(String billDate);

    /** 查询支付单状态（按对外渠道交易号）。 */
    String queryStatus(String outTradeNo);
}

