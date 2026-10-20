package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 渠道异步回调编排（支付回调 + 退款回调）—— 一个用例族：<b>把「渠道说了什么」翻译成本地状态迁移</b>。
 *
 * <p><b>为什么从 {@code PayManager} / {@code RefundManager} 里拆出来</b>（D 批拆类）：回调与发起是
 * 两类完全不同的关注点 —— 发起侧关心「先落库再调渠道」（否则窗口内丢回调），回调侧关心
 * 「验签 → 按<b>对外号</b>寻址 → 条件更新 → 命中才通知」。两者唯一的交集是「状态迁移后通知谁」，
 * 那部分已下沉到 {@link PayEventNotifier}（买家触达，ADR-042）与 {@link TradeStatusNotifier}
 * （订单状态回写）。拆开后每个类的长度回到硬约束内，且回调口径只有一份 ——
 * 支付回调与退款回调共用同一套「条件更新命中才通知」的判据，放在一起才看得出它们同构。
 *
 * <p><b>寻址口径（资金链路硬约束）</b>：回调<b>只</b>按对外号查本地单 ——
 * 支付回调按 {@code pay_order.channel_trade_no}（渠道 {@code out_trade_no}），
 * 退款回调按 {@code refund_order.out_refund_no}。内部 {@code pay_order_no} / {@code refund_no}
 * 只在本服务内定位，绝不参与渠道寻址（修复前发起侧发 {@code orders.order_no}、回调侧查
 * {@code pay_order.pay_order_no}，真实回调必然查不到）。
 *
 * <p><b>条件更新（受影响行数=1 才推进）</b>是并发/重复通知的唯一防线：渠道会重推，人工也会重放，
 * 「先读状态再改」在并发下必然写两次。命中 0 行即视为重复通知，静默返回并应答成功，
 * 避免渠道把已处理过的通知当失败无限重推（ADR-029）。
 *
 * <pre>
 * Scenario: 微信支付回调
 *   Then 整包解析（验签 + 解密 + 提取，ADR-033）→ 非成功终态仅告警不改状态
 *        → 成功则按渠道号查单 → 条件更新（PENDING→SUCCESS）命中才通知订单待发货
 * Scenario: 支付宝支付回调
 *   Then 表单参数验签 → 直接取 out_trade_no / trade_status（同为渠道号）→ 条件置成功
 * Scenario: 微信退款回调
 *   Then 按 out_refund_no 查退款单 → 条件置成功（仅处理中可迁移）→ 命中才回写售后单
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelCallbackManager {

    private final PayOrderRepository payOrderService;
    private final RefundOrderRepository refundOrderService;
    private final PaymentChannelRouter channelRouter;
    private final PayEventNotifier payEventNotifier;
    private final TradeStatusNotifier tradeStatusNotifier;

    // ==================== 支付回调 ====================

    /**
     * 处理微信支付回调（微信 v3：裸 JSON + {@code resource} 密文）。
     *
     * <p><b>非成功终态（{@code CLOSED} / {@code PAYERROR} / {@code REVOKED} 等）为何不改状态</b>：
     * 本平台支付单只有「待支付 → 成功 / 超时取消」两条迁移，引入渠道侧关闭态会牵出新的状态机分支
     * 与对账口径；而用户的支付单最终由本地超时关单（{@code PayTimeoutJob}）收敛。因此这里只记录
     * {@code trade_state} 供人工/对账排查，保持保守。
     *
     * @param headers HTTP 请求头（微信签名头 {@code Wechatpay-*}）
     * @param rawBody 回调原始报文体
     * @throws BizException {@code ILLEGAL_REQUEST} 解析失败（控制器转 500 触发渠道重推）/
     *                      {@code NO_RESOURCE_FOUND} 支付单不存在
     */
    public void handleWechatCallback(Map<String, String> headers, String rawBody) {
        WechatCallbackResult result = channelRouter.parseWechatCallback(headers, rawBody);
        if (!result.success()) {
            log.warn("微信回调非成功终态，支付单状态保持不变: channelTradeNo={}, tradeState={}, transactionId={}",
                    result.outTradeNo(), result.rawState(), result.channelTradeNo());
            return;
        }
        confirmPaid(result.outTradeNo(), result.rawState(), result.channelTradeNo());
    }

    /**
     * 处理支付宝支付回调（表单参数口径，业务字段直接在参数里可见）。
     *
     * <p>与微信路径的差别：无需解密，验签 → 直接按表单参数取 {@code out_trade_no} /
     * {@code trade_status}（协议键收在 {@link AlipayPaymentClient} 常量）。支付宝真实通道尚未接入
     * （当前仅 Mock），故此处保持既有「条件置成功」语义，不额外增加终态分支。
     *
     * @param params 支付宝异步通知表单参数
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / {@code NO_RESOURCE_FOUND} 支付单不存在
     */
    public void handleAlipayCallback(Map<String, String> params) {
        if (!channelRouter.verifyAlipayCallback(params)) {
            log.error("支付宝回调签名验证失败: channelTradeNo={}", params.get(AlipayPaymentClient.PARAM_OUT_TRADE_NO));
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败");
        }
        confirmPaid(params.get(AlipayPaymentClient.PARAM_OUT_TRADE_NO),
                params.get(AlipayPaymentClient.PARAM_TRADE_STATUS), null);
    }

    /**
     * 回调确认收款：<b>按对外渠道号</b>查单 → 条件置成功 → 命中才通知订单。并发/重复回调安全
     * （仅受影响行数=1 的调用会推进订单状态）。
     *
     * <p><b>错误承载（批次 R2 同口径）</b>：按渠道号查不到支付单 = <b>资源不存在</b>，抛
     * {@code NO_RESOURCE_FOUND(1103)} / HTTP 404。此前抛 {@code ORDER_NOT_FOUND(3009)} 属<b>语义错位</b>
     * —— 那个码名说的是「订单」，而这里查的实体是<b>支付单</b>；1103 与 3009 同为 404、
     * {@code ErrorCategory} 同为 {@code CLIENT}，故调用方（渠道、前端）<b>零感知</b>。
     *
     * @param channelTradeNo 渠道回传的 {@code out_trade_no}（= 本地 {@code channel_trade_no}）
     * @param channelState   渠道交易状态串（落 {@code result_code} 供排障）
     * @param channelTxnId   渠道交易号（微信 {@code transaction_id}，仅日志）
     * @throws BizException {@code NO_RESOURCE_FOUND} 渠道号查不到支付单
     */
    private void confirmPaid(String channelTradeNo, String channelState, String channelTxnId) {
        PayOrder payOrder = payOrderService.getByChannelTradeNo(channelTradeNo);
        if (payOrder == null) {
            log.error("支付单不存在（按对外渠道号查无）: channelTradeNo={}, channelState={}, channelTxnId={}",
                    channelTradeNo, channelState, channelTxnId);
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "支付单不存在");
        }
        if (!payOrderService.markSuccessIfPending(payOrder.getPayOrderNo(), channelState)) {
            log.info("支付回调重复（支付单非待支付状态），忽略: payOrderNo={}, channelState={}",
                    payOrder.getPayOrderNo(), channelState);
            return;
        }
        tradeStatusNotifier.notifyPaid(payOrder.getBizOrderNo());
        payEventNotifier.notifyPaySuccess(payOrder);
    }

    // ==================== 退款回调 ====================

    /**
     * 处理微信退款回调（PAY6）—— 验签解密后条件置成功并回写售后单。
     *
     * <p>非成功终态（{@code CLOSED} / {@code ABNORMAL}）仅告警、不改状态（保守：退款单终态收敛
     * 由管理端重试/对账承担，避免渠道异常态直接推翻本地状态机）。
     * 重复通知命中幂等分支（退款单非处理中）时静默返回，控制器仍应答 200 SUCCESS（ADR-029）。
     *
     * @param headers HTTP 请求头（微信签名头）
     * @param rawBody 回调原始报文
     * @throws BizException {@code ILLEGAL_REQUEST} 验签/解密失败 / {@code NO_RESOURCE_FOUND} 退款单不存在
     */
    public void handleWechatRefundCallback(Map<String, String> headers, String rawBody) {
        var result = channelRouter.parseWechatRefundCallback(headers, rawBody);
        if (!result.success()) {
            log.warn("微信退款回调非成功终态，退款单状态保持不变: outRefundNo={}, refundStatus={}, refundId={}",
                    result.outRefundNo(), result.rawRefundStatus(), result.refundId());
            return;
        }
        confirmRefunded(result.outRefundNo(), result.refundId());
    }

    /**
     * 退款确认成功：<b>按对外退款项号</b>查单 → 条件置成功（仅处理中命中）→ 命中才回写售后单。
     * 并发/重复回调安全。
     *
     * @param outRefundNo 渠道回传的 {@code out_refund_no}（= 本地 {@code out_refund_no}）
     * @param refundId    渠道退款单号（微信 {@code refund_id}，落 {@code channel_refund_no}）
     */
    private void confirmRefunded(String outRefundNo, String refundId) {
        RefundOrder refundOrder = refundOrderService.getByOutRefundNo(outRefundNo);
        if (refundOrder == null) {
            log.error("退款单不存在（按对外退款项号查无）: outRefundNo={}, refundId={}", outRefundNo, refundId);
            // 实体是「退款单」：码名与实体对齐（1103 / 404），文案带对外退款项号，便于渠道工单定位
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "退款单不存在: outRefundNo=" + outRefundNo);
        }
        if (!refundOrderService.markSuccessIfProcessing(refundOrder.getRefundNo(), refundId)) {
            log.info("退款回调重复（退款单非处理中），忽略: refundNo={}", refundOrder.getRefundNo());
            return;
        }
        payEventNotifier.onRefundSuccess(refundOrder);
    }
}
