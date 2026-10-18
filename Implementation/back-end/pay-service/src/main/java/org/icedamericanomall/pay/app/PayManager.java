package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.pay.adapter.assembler.PayOrderConverter;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.JsapiPayParamsVO;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.integration.payment.AlipayPaymentClient;
import org.icedamericanomall.integration.payment.JsapiPayResult;
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
 * <p><b>资金链路口径（本类最关键的约束）</b>：对渠道只发「对外渠道号」
 * {@code pay_order.channel_trade_no}，内部 {@code order_no} / {@code pay_order_no} <b>绝不外发</b>；
 * 回调/对账也只按渠道号寻址（{@link PayOrderRepository#getByChannelTradeNo}）。
 * 二者在此彻底分离 —— 修复前发起侧发的是 {@code orders.order_no}、回调侧却按
 * {@code pay_order.pay_order_no} 查，真实回调<b>必然查不到</b>支付单。
 *
 * <p><b>事务纪律</b>：本类方法一律<b>不</b>加 {@code @Transactional}（事务内调 Feign 属硬约束违规），
 * 需要事务的只是单条 SQL，下沉到 {@link PayOrderRepository} 的领域方法；Feign（余额扣款、
 * 通知订单状态）全在事务外执行。支付成功的买家站内信触达（ADR-042）委托
 * {@link PayEventNotifier}，同样事务外 fail-open。
 *
 * <pre>
 * Scenario: 微信/支付宝发起
 *   Then 先落/复用"待支付"支付单（含对外渠道号，跨重试不变）→ 用该渠道号调渠道拿支付链接
 *        → 条件回填链接并返回
 * Scenario: 小程序内拉起（微信 JSAPI，PAY7）
 *   Then 与微信发起同构：幂等已支付检查 → 校验订单 → 落/复用待支付单（含渠道号）
 *        → JSAPI 下单（openid 与 wechat.pay.app-id 绑定）返回带签名拉起参数集；
 *        支付结果仍由微信回调确认，此处不推进状态
 * Scenario: 余额支付
 *   Then 先落"待支付"支付单（余额渠道本地闭环，无对外渠道号）→ 事务外 Feign 扣余额
 *        → 扣款成功条件置成功并通知订单；扣款失败则条件取消支付单并抛 BALANCE_INSUFFICIENT
 * Scenario: 回调/超时
 *   Then 微信回调经通道适配层整包解析（验签 + 解密 + 提取强类型结果，ADR-033），
 *        非成功终态仅告警不改状态；成功则<b>按渠道号</b>查支付单 → 条件更新（PENDING→SUCCESS）
 *        命中才通知订单待发货；支付宝回调走表单参数验签 + 直接取键（同为渠道号）；
 *        超时条件取消（PENDING→TIMEOUT_CANCEL）命中才通知订单已取消，并发/重复调用安全
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
     * <p><b>调用顺序不可颠倒</b>：先落/复用「待支付」支付单（对外渠道号在此定稿），
     * 再用该渠道号调渠道拿链接，最后条件回填链接。这样任何时刻收到的渠道回调都能按渠道号
     * 查回本地支付单 —— 反过来（先调渠道后落库）会在「渠道已受理、本地尚未落库」的窗口内
     * 必然丢回调。
     *
     * @param orderNo 业务订单号
     * @param userId  支付用户
     * @param channel 支付渠道
     * @return 支付单视图（含支付链接或已支付状态）
     * @throws BizException 订单不存在（ORDER_NOT_FOUND）/ 已支付 / 订单摘要金额不完整
     *                      （{@code INTERNAL_ERROR}/500，上游契约违约，不可重试）/
     *                      余额不足（BALANCE_INSUFFICIENT）
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
        PayOrder payOrder = reuseOrCreatePending(existing, orderNo, userId, amount, channel,
                PayTypeEnum.NATIVE.getCode());
        String payUrl = channelRouter.initiatePayment(payOrder.getChannelTradeNo(), amount, PAY_DESC, channel);
        payOrderService.attachPayUrl(payOrder, payUrl);
        return payOrderConverter.toVO(payOrder);
    }

    /**
     * 发起微信 JSAPI 支付（小程序内拉起，PAY7）—— 与 {@link #initiatePayment} 同构：
     * 幂等已支付检查 → Feign 校验订单 → 落/复用待支付单（对外渠道号定稿）→
     * 渠道 JSAPI 下单（appid/openid 同源绑定由 {@code wechat.pay.app-id} 配置侧保证）。
     *
     * <p>返回<b>带签名</b>的拉起参数集，前端直传 {@code wx.requestPayment}；支付结果不经本方法
     * 推进——仍走 PAY2 微信回调 → {@link #confirmPaid} 全链路，此处零额外状态分支。
     * JSAPI 的拉起参数不留存（{@code qrCodeUrl} 保持 {@code null}），故无链接回填步骤。
     *
     * @param orderNo 业务订单号
     * @param userId  支付用户
     * @param openid  用户在下单 appid（小程序 appid）下的 openid
     * @return 小程序拉起参数集（appId/timeStamp/nonceStr/packageVal/signType/paySign）
     * @throws BizException 已支付 / 订单不存在（ORDER_NOT_FOUND）/ 订单摘要金额不完整
     *                      （{@code INTERNAL_ERROR}/500）/ 渠道下单失败
     */
    public JsapiPayParamsVO initiateJsapiPayment(String orderNo, Long userId, String openid) {
        PayOrder existing = payOrderService.getByBizOrderNo(orderNo);
        if (existing != null && existing.isPaid()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "订单已支付");
        }
        OrderSummaryCO summary = orderClient.getOrder(orderNo);
        if (summary == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderNo);
        }
        int amount = resolvePayAmount(summary, orderNo);
        PayOrder payOrder = reuseOrCreatePending(existing, orderNo, userId, amount, PayChannelEnum.WECHAT,
                PayTypeEnum.MINI.getCode());
        JsapiPayResult params = channelRouter.initiateWechatJsapi(payOrder.getChannelTradeNo(), amount,
                PAY_DESC, openid);
        return payOrderConverter.toParamsVO(params);
    }

    /**
     * 扣款金额取「应付 payAmount」；历史订单缺 {@code payAmount} 时降级用原价 {@code totalAmount} 并告警。
     *
     * <p><b>两个都不可用 = 上游契约违约，不是「你参数错」（批次 R2）</b>：{@code totalAmount} 来自
     * trade-service 的订单摘要（Feign {@code OrderClient#getOrder}）—— 摘要<b>返回 200 但缺金额</b>
     * 属<b>上游契约违约</b>，归 {@code INTERNAL_ERROR(1000)} / HTTP 500 / {@code BUG} / 不可重试
     * （{@code collaboration/09-constraints.md} §三·六 四类划分：服务端数据不一致 ≠ 调用方错误）。
     *
     * <p><b>为什么不用 {@code SYSTEM_BUSY} / {@code UpstreamException}</b>：那是「服务<b>不可用</b>」
     * （网络/超时/熔断，重试有机会成功）的承载；本条是「服务可用，但回的数据<b>不完整</b>」——
     * 重试必然重复拿到同一个残缺报文。把它说成 {@code SYSTEM_BUSY} 是<b>另一个方向的误报</b>
     * （历史缺陷 R-4 就是「把数据问题说成服务不可用」这个病），还会误导调用方进入退避重试。
     *
     * <p><b>文案口径</b>：带 {@code orderNo}（业务标识、<b>不含 PII</b>），让运维能直接定位到
     * 哪张订单的摘要坏了，无需回问调用方。
     */
    private int resolvePayAmount(OrderSummaryCO summary, String orderNo) {
        Integer payAmount = summary.getPayAmount();
        if (payAmount != null && payAmount > 0) {
            return payAmount;
        }
        Integer totalAmount = summary.getTotalAmount();
        if (totalAmount == null || totalAmount <= 0) {
            // 应付金额与原价同时不可用 = 上游契约违约（1000/500/BUG/不可重试），不是 400「你参数错」
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "订单摘要金额不完整（缺少应付金额与原价）: orderNo=" + orderNo);
        }
        log.warn("订单缺少应付金额(payAmount)，降级使用原价(totalAmount): orderNo={}, totalAmount={}",
                orderNo, totalAmount);
        return totalAmount;
    }

    /**
     * 复用已有「待支付」支付单（条件重置），否则新建，避免同一订单重复新增支付行。
     *
     * <p><b>对外渠道号跨重试稳定</b>：复用路径不改 {@code channel_trade_no}（聚合的
     * {@link PayOrder#applyPendingReset} 刻意不碰该字段），新建路径由
     * {@link PayOrder#newPending} 现生成一个 —— 两条路径都不会出现「同一笔支付两个对外号」。
     */
    private PayOrder reuseOrCreatePending(PayOrder existing, String orderNo, Long userId,
                                          int amount, PayChannelEnum channel, int payType) {
        if (existing != null && existing.isPendingPay()) {
            PayOrder reused = payOrderService.resetPending(existing, amount, channel.getCode(), payType);
            if (reused != null) {
                return reused;
            }
        }
        return payOrderService.createPending(orderNo, userId, amount, channel.getCode(), payType);
    }

    /**
     * 余额支付：落"待支付"支付单（本地事务）→ 事务外 Feign 扣款 → 条件置成功并通知订单。
     *
     * <p>余额渠道本地闭环，不对外发起，故 {@code channel_trade_no} 为 {@code null}
     * （见 {@link PayOrder#newChannelTradeNo}）。
     *
     * @throws BizException 余额不足（BALANCE_INSUFFICIENT）—— 支付单已被条件取消
     */
    private PayOrder payByBalance(String orderNo, Long userId, int amount) {
        PayOrder payOrder = payOrderService.createPending(orderNo, userId, amount,
                PayChannelEnum.BALANCE.getCode(), PayTypeEnum.BALANCE.getCode());
        try {
            channelRouter.deductBalance(userId, amount, payOrder.getPayOrderNo());
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
     * 仅告警并返回（<b>不改支付单状态</b>）→ <b>按对外渠道号</b>查单（不存在抛 {@code NO_RESOURCE_FOUND}）→
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
     * {@code ErrorCategory} 同为 {@code CLIENT}，故调用方（渠道、前端）<b>零感知</b>，
     * 只是把码名与实体对齐（与 I40/AD43/PAY6/I55 的处理一致）。
     *
     * @param channelTradeNo  渠道回传的 {@code out_trade_no}（= 本地 {@code channel_trade_no}）
     * @param channelState    渠道交易状态串（落 {@code result_code} 供排障）
     * @param channelTxnId    渠道交易号（微信 {@code transaction_id}，仅日志）
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
