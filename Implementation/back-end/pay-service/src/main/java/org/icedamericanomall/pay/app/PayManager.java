package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.pay.adapter.assembler.PayOrderConverter;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.WechatCallbackResult;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.enums.PayTypeEnum;
import org.icedamericanomall.pay.domain.enums.TradeOrderStatusEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付编排 Manager —— 按渠道（微信/支付宝/余额）路由发起支付与回调，编排跨服务 Feign。
 * 领域内的支付单读写下沉至 {@link PayOrderRepository}。
 *
 * <p><b>事务纪律</b>：本类方法一律<b>不</b>加 {@code @Transactional}（事务内调 Feign 属硬约束违规），
 * 需要事务的只是单条 SQL，下沉到 {@link PayOrderRepository} 的领域方法；Feign（余额扣款、
 * 通知订单状态）全在事务外执行。支付成功的买家站内信触达（ADR-042）委托
 * {@link PayEventNotifier}，同样事务外 fail-open。
 *
 * <pre>
 * Scenario: 微信/支付宝发起
 *   Then 调对应渠道客户端拿支付链接，复用或创建"待支付"支付单，返回链接
 * Scenario: 余额支付
 *   Then 先落"待支付"支付单 → 事务外 Feign 扣余额 → 扣款成功条件置成功并通知订单；
 *        扣款失败则条件取消支付单并抛 BALANCE_INSUFFICIENT（禁止只记日志吞掉）
 * Scenario: 回调/超时
 *   Then 微信回调经通道适配层整包解析（验签 + 解密 + 提取强类型结果，ADR-033），
 *        非成功终态仅告警不改状态；成功则条件更新（PENDING→SUCCESS）命中才通知订单待发货；
 *        支付宝回调走表单参数验签 + 直接取键；超时条件取消（PENDING→TIMEOUT_CANCEL）命中才通知订单已取消，
 *        并发/重复调用安全
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayManager {

    private static final String PAY_DESC = "订单支付";

    private final PayOrderRepository payOrderService;
    private final PayOrderConverter payOrderConverter;
    private final OrderClient orderClient;
    private final PaymentChannelRouter channelRouter;
    private final PayEventNotifier payEventNotifier;

    /**
     * 发起支付（微信/支付宝/余额）。
     *
     * @param orderNo 业务订单号
     * @param userId  支付用户
     * @param channel 支付渠道
     * @return 支付单视图（含支付链接或已支付状态）
     * @throws BizException 订单不存在（ORDER_NOT_FOUND）/ 已支付 / 余额不足（BALANCE_INSUFFICIENT）
     */
    public PayOrderVO initiatePayment(String orderNo, Long userId, PayChannelEnum channel) {
        PayOrder existing = payOrderService.getByBizOrderNo(orderNo);
        if (existing != null && existing.isPaid()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单已支付");
        }
        OrderSummaryCO summary = orderClient.getOrder(orderNo);
        if (summary == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        int amount = resolvePayAmount(summary, orderNo);
        if (channel == PayChannelEnum.BALANCE) {
            return payOrderConverter.toVO(payByBalance(orderNo, userId, amount));
        }
        String payUrl = channelRouter.initiatePayment(orderNo, amount, PAY_DESC, channel);
        PayOrder payOrder = reuseOrCreatePending(existing, orderNo, userId, amount, payUrl, channel);
        return payOrderConverter.toVO(payOrder);
    }

    /** 扣款金额取「应付 payAmount」；历史订单缺字段时降级用原价并告警。 */
    private int resolvePayAmount(OrderSummaryCO summary, String orderNo) {
        Integer payAmount = summary.getPayAmount();
        if (payAmount != null && payAmount > 0) {
            return payAmount;
        }
        Integer totalAmount = summary.getTotalAmount();
        if (totalAmount == null || totalAmount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单金额非法: " + orderNo);
        }
        log.warn("订单缺少应付金额(payAmount)，降级使用原价(totalAmount): orderNo={}, totalAmount={}",
                orderNo, totalAmount);
        return totalAmount;
    }

    /** 复用已有「待支付」支付单（条件更新），否则新建，避免同一订单重复新增支付行。 */
    private PayOrder reuseOrCreatePending(PayOrder existing, String orderNo, Long userId,
                                                int amount, String payUrl, PayChannelEnum channel) {
        int payType = PayTypeEnum.NATIVE.getCode();
        if (existing != null && existing.isPendingPay()) {
            PayOrder reused = payOrderService.resetPending(
                    existing, amount, channel.getCode(), payUrl, payType);
            if (reused != null) {
                return reused;
            }
        }
        return payOrderService.createPending(orderNo, userId, amount, payUrl, channel.getCode(), payType);
    }

    /**
     * 余额支付：落"待支付"支付单（本地事务）→ 事务外 Feign 扣款 → 条件置成功并通知订单。
     *
     * @throws BizException 余额不足（BALANCE_INSUFFICIENT）—— 支付单已被条件取消
     */
    private PayOrder payByBalance(String orderNo, Long userId, int amount) {
        PayOrder payOrder = payOrderService.createPending(orderNo, userId, amount, null,
                PayChannelEnum.BALANCE.getCode(), PayTypeEnum.BALANCE.getCode());
        try {
            channelRouter.deductBalance(userId, amount);
        } catch (Exception e) {
            payOrderService.cancelPending(payOrder.getId());
            log.error("余额扣款失败，支付单已取消: userId={}, amount={}, orderNo={}", userId, amount, orderNo, e);
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足，支付已取消");
        }
        if (!payOrderService.markBalancePaidIfPending(payOrder.getId())) {
            log.warn("余额已扣但支付单未命中状态迁移，需人工核对: payOrderNo={}", payOrder.getPayOrderNo());
        }
        payOrder.markPaid(LocalDateTime.now());
        notifyOrderPaid(orderNo);
        payEventNotifier.notifyPaySuccess(payOrder);
        return payOrder;
    }

    /**
     * 处理微信支付回调（微信 v3：裸 JSON + {@code resource} 密文）。
     *
     * <p><b>时序</b>：通道适配层整包解析（验签 + AES-GCM 解密 + 提取，见 ADR-033）→ 非成功终态则
     * 仅告警并返回（<b>不改支付单状态</b>）→ 按支付单号查单（不存在抛 {@code ORDER_NOT_FOUND}）→
     * 条件置成功（仅 {@code PENDING_PAY} 命中才通知订单待发货；未命中视为重复通知，静默返回）。
     *
     * <p><b>非成功终态（{@code CLOSED} / {@code PAYERROR} / {@code REVOKED} 等）为何不改状态</b>：
     * 本平台支付单只有「待支付 → 成功 / 超时取消」两条迁移，引入渠道侧关闭态会牵出新的状态机分支
     * 与对账口径；而用户的支付单最终由本地超时关单（{@code PayTimeoutJob}）收敛。因此这里只记录
     * {@code trade_state} 供人工/对账排查，保持保守。
     *
     * @param headers HTTP 请求头（微信签名头 {@code Wechatpay-*}）
     * @param rawBody 回调原始报文体
     * @throws BizException {@code ILLEGAL_REQUEST} 解析失败（控制器转 500 触发渠道重推）/
     *                      {@code ORDER_NOT_FOUND} 支付单不存在
     */
    public void handleWechatCallback(Map<String, String> headers, String rawBody) {
        WechatCallbackResult result = channelRouter.parseWechatCallback(headers, rawBody);
        if (!result.success()) {
            log.warn("微信回调非成功终态，支付单状态保持不变: payOrderNo={}, tradeState={}, transactionId={}",
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
     * @throws BizException {@code ILLEGAL_REQUEST} 验签失败 / {@code ORDER_NOT_FOUND} 支付单不存在
     */
    public void handleAlipayCallback(Map<String, String> params) {
        if (!channelRouter.verifyAlipayCallback(params)) {
            log.error("支付宝回调签名验证失败: payOrderNo={}", params.get(AlipayPaymentClient.PARAM_OUT_TRADE_NO));
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "支付回调验证失败");
        }
        confirmPaid(params.get(AlipayPaymentClient.PARAM_OUT_TRADE_NO),
                params.get(AlipayPaymentClient.PARAM_TRADE_STATUS), null);
    }

    /**
     * 回调确认收款：查单 → 条件置成功 → 命中才通知订单。并发/重复回调安全
     * （仅受影响行数=1 的调用会推进订单状态）。
     */
    private void confirmPaid(String payOrderNo, String channelState, String channelTradeNo) {
        PayOrder payOrder = payOrderService.getByPayOrderNo(payOrderNo);
        if (payOrder == null) {
            log.error("支付单不存在: payOrderNo={}, channelState={}, channelTradeNo={}",
                    payOrderNo, channelState, channelTradeNo);
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在");
        }
        if (!payOrderService.markSuccessIfPending(payOrderNo, channelState)) {
            log.info("支付回调重复（支付单非待支付状态），忽略: payOrderNo={}, channelState={}",
                    payOrderNo, channelState);
            return;
        }
        notifyOrderPaid(payOrder.getBizOrderNo());
        payEventNotifier.notifyPaySuccess(payOrder);
    }

    /**
     * 查询支付单状态。
     *
     * @param payOrderNo 支付单号
     * @return 支付单视图；不存在时字段为空对象
     */
    public PayOrderVO queryStatus(String payOrderNo) {
        return payOrderConverter.toVO(payOrderService.getByPayOrderNo(payOrderNo));
    }

    /**
     * 超时关单：条件取消支付单（仅 PENDING_PAY 命中）后通知 trade-service 取消订单。
     * 已支付的支付单不会被取消，也不会连带取消订单。
     */
    public void cancelTimeoutPayOrders() {
        List<PayOrder> timeoutOrders = payOrderService.listTimeout(LocalDateTime.now());
        for (PayOrder payOrder : timeoutOrders) {
            try {
                if (!payOrderService.cancelPending(payOrder.getId())) {
                    log.info("支付单已支付/已取消，跳过订单取消: payOrderNo={}", payOrder.getPayOrderNo());
                    continue;
                }
                cancelOrderQuietly(payOrder.getBizOrderNo());
                log.info("支付超时已取消: payOrderNo={}, orderNo={}",
                        payOrder.getPayOrderNo(), payOrder.getBizOrderNo());
            } catch (Exception e) {
                log.error("处理支付超时订单失败: payOrderId={}", payOrder.getId(), e);
            }
        }
    }

    /** Feign：通知 trade-service 订单进入待发货（非致命，失败可对账修复）。 */
    private void notifyOrderPaid(String orderNo) {
        try {
            orderClient.updateOrderStatus(orderNo, TradeOrderStatusEnum.PENDING_SHIPMENT.getCode());
            log.info("支付成功，订单状态已更新: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("支付成功但订单状态更新失败: orderNo={}", orderNo, e);
        }
    }

    /** Feign：通知 trade-service 取消订单（非致命，超时订单也可由 trade 侧兜底任务取消）。 */
    private void cancelOrderQuietly(String orderNo) {
        try {
            orderClient.updateOrderStatus(orderNo, TradeOrderStatusEnum.CANCELLED.getCode());
        } catch (Exception e) {
            log.error("支付超时取消订单失败: orderNo={}", orderNo, e);
        }
    }
}
