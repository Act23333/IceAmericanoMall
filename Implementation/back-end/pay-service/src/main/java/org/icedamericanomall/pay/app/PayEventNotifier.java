package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TradeInvoiceClient;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.InvoicePartialRefundCO;
import org.icedamericanomall.dto.InvoiceVoidCO;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.springframework.stereotype.Component;

/**
 * pay 事件副作用协作器（ADR-042）—— 支付成功 / 退款成功两事件的买家站内信单出口，
 * 及退款成功的三步收口（I42 售后回写 → 买家触达 → 发票联动：全额退款 I56 冲红/作废 ·
 * 部分退款 I60 置标，V5.16.1）。
 *
 * <p><b>职责</b>：封装 {@link NotificationClient#sendInAppMessage} 调用、type 常量与 fail-open
 * 样板（发送失败仅 log.warn，不重试、不回滚，绝不影响支付/退款主流程）；退款成功路径同时承接
 * 自 {@code RefundManager} 整体迁入的 I42 售后单回写（先 I42 回写、后买家触达，各自独立 try/catch），
 * 以及退款成功发票联动（PRD D4：退款金额 = 支付单全额 → I56 通知 trade 冲红/作废；
 * 部分退款 → I60 置 partial_refund_flag=1 待人工处理，US-INV-8）。
 *
 * <p><b>事务纪律</b>：两个触发点（{@code PayManager#confirmPaid} / {@code payByBalance} 与
 * {@code RefundManager#markSuccessAndNotify} / {@code confirmRefunded}）均无本地事务包裹——
 * 单条条件更新在仓储层自提交，发送发生在落库成功之后、事务边界之外（Feign 严禁进事务）。
 *
 * <p><b>sender 口径</b>：系统通知 senderId 置空，senderName/avatar 置空串由前端渲染
 * （对齐 item {@code ReviewManager} 先例，不查用户名）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayEventNotifier {

    private static final String NOTIFY_TYPE_PAY_SUCCESS = "PAY_SUCCESS";
    private static final String NOTIFY_TYPE_REFUND_SUCCESS = "REFUND_SUCCESS";
    private static final String NOTIFY_TITLE_PAY_SUCCESS = "支付成功通知";
    private static final String NOTIFY_TITLE_REFUND_SUCCESS = "退款成功通知";
    /** 买家订单链接前缀（前端路由），四事件统一口径。 */
    private static final String ORDER_LINK_PREFIX = "/orders/";

    private final NotificationClient notificationClient;
    private final AfterSaleClient afterSaleClient;
    private final TradeInvoiceClient tradeInvoiceClient;
    private final PayOrderRepository payOrderService;

    /**
     * 支付成功买家触达（ADR-042）—— 站内信携带订单号 + 实付金额。
     *
     * @param payOrder 已置成功的支付单聚合（含 bizUserId / bizOrderNo / amount）；收件人缺失时跳过
     */
    public void notifyPaySuccess(PayOrder payOrder) {
        if (payOrder == null || payOrder.getBizUserId() == null) {
            log.warn("[pay-notify][skip] 支付成功买家触达跳过（支付单或收件人缺失）");
            return;
        }
        String orderNo = payOrder.getBizOrderNo();
        String amountText = payOrder.getAmount() == null ? ""
                : "，实付金额 " + toYuanDisplay(payOrder.getAmount()) + " 元";
        sendInApp(payOrder.getBizUserId(), NOTIFY_TYPE_PAY_SUCCESS, NOTIFY_TITLE_PAY_SUCCESS,
                "您的订单 " + orderNo + " 已支付成功" + amountText + "，我们将尽快为您发货",
                ORDER_LINK_PREFIX + orderNo);
    }

    /**
     * 退款成功收口（ADR-042 + I56/I60）：先 I42 回写售后单、后买家站内信触达，再发票联动。
     *
     * <p>共享结算入口（PAY6 回调 / 渠道同步终态 / 悬挂补偿 / I55 无售后单退款）统一经本方法收口；
     * 三步各自独立 try/catch，互不影响，也不影响退款落账。
     *
     * <p><b>无售后单退款泛化（I55，ADR-049）</b>：{@code afterSaleId = null}（拼团等无售后场景）
     * 跳过 I42 回写（无售后域可回写），买家 {@code REFUND_SUCCESS} 站内信照常发送；
     * 售后退款（I40）行为不变。
     *
     * @param refundOrder 已置成功的退款单聚合（含 afterSaleId 可空 / userId / orderNo / refundAmount / channel / bizRef）
     */
    public void onRefundSuccess(RefundOrder refundOrder) {
        if (refundOrder == null) {
            return;
        }
        if (refundOrder.getAfterSaleId() != null) {
            writeBackAfterSaleRefunded(refundOrder.getAfterSaleId());
        }
        notifyBuyerRefundSuccess(refundOrder);
        triggerInvoiceLinkage(refundOrder);
    }

    /**
     * 回写 trade 售后单为 REFUNDED（I42）。
     *
     * <p><b>fail-open</b>：状态回写丢失不阻断退款主流程（由对账/管理端重试收敛）；
     * {@code AfterSaleClientFallback} 本身即 fail-open，此处再加一层防御避免任何异常影响退款落账。
     */
    private void writeBackAfterSaleRefunded(Long afterSaleId) {
        try {
            afterSaleClient.markRefunded(afterSaleId);
            log.info("退款成功，售后单已回写 REFUNDED: afterSaleId={}", afterSaleId);
        } catch (Exception e) {
            log.warn("退款成功但售后单回写失败（fail-open，待对账/重试收敛）: afterSaleId={}", afterSaleId, e);
        }
    }

    /** 退款成功买家触达：站内信携带订单号 + 退款金额 + 原路渠道；收件人缺失时跳过。 */
    private void notifyBuyerRefundSuccess(RefundOrder refundOrder) {
        if (refundOrder.getUserId() == null) {
            log.warn("[refund-notify][skip] 退款成功买家触达跳过（收件人缺失）: refundNo={}",
                    refundOrder.getRefundNo());
            return;
        }
        String orderNo = refundOrder.getOrderNo();
        String amountText = refundOrder.getRefundAmount() == null ? ""
                : "，退款金额 " + toYuanDisplay(refundOrder.getRefundAmount()) + " 元";
        sendInApp(refundOrder.getUserId(), NOTIFY_TYPE_REFUND_SUCCESS, NOTIFY_TITLE_REFUND_SUCCESS,
                "您的订单 " + orderNo + " 已退款成功" + amountText + "，将原路退回至"
                        + channelDesc(refundOrder.getChannel()),
                ORDER_LINK_PREFIX + orderNo);
    }

    /**
     * 退款成功发票联动（I56 / I60，契约 §4.14.16c；PRD D4 / US-INV-8）——按退款金额二分：
     * <ul>
     *   <li>退款金额 = 支付单实付全额 → {@code voidOnFullRefund}（I56，自动冲红/作废）；</li>
     *   <li>退款金额 &lt; 支付单实付（部分退款）→ {@code markPartialRefund}（I60，有效发票置
     *       {@code partial_refund_flag=1} 待人工处理——D4 Phase 1 简化口径：不自动红字，
     *       管理端"部分退款待人工处理"筛选人工收敛，Phase 2 再按金额精确部分红字）。</li>
     * </ul>
     *
     * <p><b>fail-open 双保险</b>：{@code TradeInvoiceClientFallback} 已兜一层降级告警，此处再捕获
     * 任何异常仅 log.warn，绝不影响退款落账主流程（与 I42 回写同口径，两条分支同享该防御）。
     */
    private void triggerInvoiceLinkage(RefundOrder refundOrder) {
        try {
            if (isBlank(refundOrder.getOrderNo()) || isBlank(refundOrder.getBizRef())) {
                log.warn("[refund-invoice][skip] 订单号或幂等业务键缺失，发票联动跳过: refundNo={}",
                        refundOrder.getRefundNo());
                return;
            }
            PayOrder payOrder = payOrderService.getByPayOrderNo(refundOrder.getPayOrderNo());
            if (payOrder == null || payOrder.getAmount() == null || refundOrder.getRefundAmount() == null) {
                log.warn("[refund-invoice][skip] 支付单或金额缺失，无法判定全额/部分，发票联动跳过: refundNo={}, payOrderNo={}",
                        refundOrder.getRefundNo(), refundOrder.getPayOrderNo());
                return;
            }
            if (refundOrder.getRefundAmount().intValue() == payOrder.getAmount().intValue()) {
                tradeInvoiceClient.voidOnFullRefund(
                        new InvoiceVoidCO(refundOrder.getOrderNo(), refundOrder.getBizRef()));
                log.info("[refund-invoice][triggered] 全额退款已联动发票冲红/作废（I56）: orderNo={}, sceneRef={}",
                        refundOrder.getOrderNo(), refundOrder.getBizRef());
            } else {
                tradeInvoiceClient.markPartialRefund(
                        new InvoicePartialRefundCO(refundOrder.getOrderNo(), refundOrder.getBizRef()));
                log.info("[refund-invoice][triggered] 部分退款已联动发票置标待人工处理（I60）: orderNo={}, sceneRef={}, refundAmount={}, paidAmount={}",
                        refundOrder.getOrderNo(), refundOrder.getBizRef(),
                        refundOrder.getRefundAmount(), payOrder.getAmount());
            }
        } catch (Exception e) {
            log.warn("[refund-invoice][fail-open] 发票联动失败（不阻断退款主流程，管理端人工收敛）: refundNo={}, orderNo={}",
                    refundOrder.getRefundNo(), refundOrder.getOrderNo(), e);
        }
    }

    /** 统一发送（fail-open）：任何异常仅告警，不外溢。 */
    private void sendInApp(Long recipientId, String type, String title, String content, String linkUrl) {
        try {
            InAppMessageSendReqCO message = new InAppMessageSendReqCO();
            message.setRecipientId(recipientId);
            // 系统通知：senderId 置空；senderName/avatar 置空串由前端按类型渲染（不查用户名）
            message.setSenderName("");
            message.setSenderAvatar("");
            message.setType(type);
            message.setTitle(title);
            message.setContent(content);
            message.setLinkUrl(linkUrl);
            notificationClient.sendInAppMessage(message);
        } catch (Exception e) {
            log.warn("发送买家触达失败(type={}, recipient={}): {}", type, recipientId, e.getMessage());
        }
    }

    /** 分 → 元展示串（纯整数运算，避免浮点误差）。 */
    private static String toYuanDisplay(int amountInCents) {
        return amountInCents / 100 + "." + String.format("%02d", amountInCents % 100);
    }

    /** 渠道展示名；未知渠道降级为原始码——触达路径绝不抛异常。 */
    private static String channelDesc(String channel) {
        try {
            return PayChannelEnum.of(channel).getDesc();
        } catch (Exception e) {
            return channel == null ? "" : channel;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
