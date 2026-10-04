package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AfterSaleClient;
import org.icedamericanomall.client.BalanceClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.BalanceRefundCO;
import org.icedamericanomall.dto.RefundCreateCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.integration.payment.ChannelRefundResult;
import org.icedamericanomall.pay.adapter.assembler.RefundConverter;
import org.icedamericanomall.pay.adapter.response.RefundVO;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.enums.PayChannelEnum;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 退款编排 Manager（F1 买家退款资金流）—— pay-service 退款用例族的唯一编排入口。
 *
 * <p><b>职责边界（ADR-025 用例族划分）</b>：{@code RefundManager} 独占退款单（{@code refund_order}）
 * 的生命周期，独立于 {@code PayManager}（支付单用例族）。两者只在「读支付单取渠道/金额」上单向依赖。
 * 出参装配（Domain → VO / CO）内聚在本层，控制器不持有转换器。
 *
 * <p><b>事务纪律</b>：本类方法一律<b>不</b>加 {@code @Transactional} ——
 * 渠道调用（Wallet/微信/支付宝）与回写 trade 都是 Feign，事务内调 Feign 属硬约束违规。
 * 需要事务的写入下沉到 {@link RefundOrderRepository} 的条件更新。
 *
 * <pre>
 * Scenario: 余额渠道退款
 *   Then 调 I41 余额入账（transactionNo=refundNo 幂等）→ 同步成功 → 退款单置成功 → I42 回写售后单
 * Scenario: 微信/支付宝渠道退款
 *   Then 调渠道退款 API；支付宝 Mock 同步成功即置成功；微信保持处理中，等 PAY6 回调
 * Scenario: 同售后单重复请求（幂等）
 *   Then 返回既有退款单，不新建（uk_after_sale 兜底）
 * Scenario: 渠道退款失败
 *   Then 退款单置失败并记录 failReason，售后单停留 APPROVED（异常向上透出，管理端 AD43 重试）
 * Scenario: 微信退款回调成功
 *   Then 退款单条件置成功（仅处理中可迁移，重复通知幂等）→ I42 回写售后单
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundManager {

    /** {@code refund_order.fail_reason} 列长上限 255，留出安全余量。 */
    static final int MAX_FAIL_REASON_LENGTH = 200;

    private final RefundOrderRepository refundOrderService;
    private final PayOrderRepository payOrderService;
    private final PaymentChannelRouter channelRouter;
    private final BalanceClient balanceClient;
    private final AfterSaleClient afterSaleClient;
    private final RefundConverter refundConverter;

    /**
     * 发起退款（I40）—— 同售后单幂等。
     *
     * @param req 退款发起参数
     * @return I40 内部契约（字段子集：refundNo + status）
     * @throws BadRequestException 入参非法
     * @throws BizException        支付单不存在（{@code ORDER_NOT_FOUND}）/ 退款金额超实付（{@code PARAM_ERROR}）/
     *                             渠道退款失败（原异常透出，退款单已置失败）
     */
    public RefundResultCO refund(RefundCreateCO req) {
        return refundConverter.toResultCO(doRefund(req));
    }

    /** 发起退款领域流程（返回聚合，出参装配由 {@link #refund} 承担）。 */
    private RefundOrder doRefund(RefundCreateCO req) {
        validate(req);
        RefundOrder existing = refundOrderService.getByAfterSaleId(req.getAfterSaleId());
        if (existing != null) {
            log.info("[refund][idempotent] afterSaleId={} 已有退款单，返回既有: refundNo={}, status={}",
                    req.getAfterSaleId(), existing.getRefundNo(), existing.getStatus());
            return existing;
        }
        PayOrder payOrder = requirePayOrder(req);
        RefundOrder refundOrder;
        try {
            refundOrder = refundOrderService.createProcessing(req.getAfterSaleId(), req.getOrderNo(),
                    payOrder.getPayOrderNo(), payOrder.getBizUserId(), payOrder.getPayChannelCode(),
                    req.getRefundAmount());
        } catch (DuplicateKeyException e) {
            // 并发孪生请求已建单：回落为「返回既有退款单」（uk_after_sale 兜底）
            RefundOrder concurrent = refundOrderService.getByAfterSaleId(req.getAfterSaleId());
            if (concurrent == null) {
                throw e;
            }
            log.info("[refund][idempotent][concurrent] afterSaleId={} 并发建单，返回既有: refundNo={}",
                    req.getAfterSaleId(), concurrent.getRefundNo());
            return concurrent;
        }
        return execute(refundOrder, payOrder, req.getReason());
    }

    /**
     * 管理端重试（AD43）—— 仅「失败」退款单可重试。
     *
     * @param refundNo 退款单号
     * @return 重试后的退款单视图
     * @throws BizException 退款单不存在（{@code NO_RESOURCE_FOUND}）/ 非失败状态（{@code REFUND_STATUS_INVALID}，
     *                      由 {@link RefundOrder#assertRetryable} 断言）/ 支付单缺失 / 渠道再次失败（原异常透出）
     */
    public RefundVO retry(String refundNo) {
        return refundConverter.toVO(doRetry(refundNo));
    }

    /** 管理端重试领域流程（返回聚合，出参装配由 {@link #retry} 承担）。 */
    private RefundOrder doRetry(String refundNo) {
        RefundOrder refundOrder = refundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "退款单不存在: " + refundNo);
        }
        // 「只有失败单可重试」是退款单聚合的不变量，断言归聚合所有（应用层不得自行判断状态码）
        refundOrder.assertRetryable();
        PayOrder payOrder = payOrderService.getByPayOrderNo(refundOrder.getPayOrderNo());
        if (payOrder == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在: " + refundOrder.getPayOrderNo());
        }
        if (!refundOrderService.markProcessingIfFailed(refundNo)) {
            // 条件更新未命中 = 并发下已被其它请求推进（重复点击重试安全）
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID, "当前退款状态不允许该操作");
        }
        log.info("[refund][retry] refundNo={} 重置为处理中并重新发起渠道退款", refundNo);
        return execute(refundOrder, payOrder, null);
    }

    /**
     * 处理微信退款回调（PAY6）—— 验签解密后条件置成功并回写售后单。
     *
     * <p>非成功终态（{@code CLOSED} / {@code ABNORMAL}）仅告警、不改状态（保守：退款单终态收敛
     * 由管理端重试/对账承担，避免渠道异常态直接推翻本地状态机）。
     * 重复通知命中幂等分支（退款单非处理中）时静默返回，控制器仍应答 200 SUCCESS（ADR-029）。
     *
     * @param headers HTTP 请求头（微信签名头）
     * @param rawBody 回调原始报文
     * @throws BizException {@code ILLEGAL_REQUEST} 验签/解密失败 / {@code ORDER_NOT_FOUND} 退款单不存在
     */
    public void handleWechatRefundCallback(Map<String, String> headers, String rawBody) {
        var result = channelRouter.parseWechatRefundCallback(headers, rawBody);
        if (!result.success()) {
            log.warn("微信退款回调非成功终态，退款单状态保持不变: refundNo={}, refundStatus={}, refundId={}",
                    result.outRefundNo(), result.rawRefundStatus(), result.refundId());
            return;
        }
        confirmRefunded(result.outRefundNo(), result.refundId());
    }

    /** 退款确认成功：查单 → 条件置成功（仅处理中命中）→ 命中才回写售后单。并发/重复回调安全。 */
    private void confirmRefunded(String refundNo, String channelRefundNo) {
        RefundOrder refundOrder = refundOrderService.getByRefundNo(refundNo);
        if (refundOrder == null) {
            log.error("退款单不存在: refundNo={}, channelRefundNo={}", refundNo, channelRefundNo);
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "退款单不存在");
        }
        if (!refundOrderService.markSuccessIfProcessing(refundNo, channelRefundNo)) {
            log.info("退款回调重复（退款单非处理中），忽略: refundNo={}", refundNo);
            return;
        }
        notifyAfterSaleRefunded(refundOrder.getAfterSaleId());
    }

    /** 执行渠道退款：成功同步置成功；处理中保持；失败置失败并向上透出（fail-closed）。 */
    private RefundOrder execute(RefundOrder refundOrder, PayOrder payOrder, String reason) {
        String refundNo = refundOrder.getRefundNo();
        try {
            ChannelRefundResult result = requestChannel(payOrder, refundOrder, reason);
            if (result.settled()) {
                markSuccessAndNotify(refundNo, result.channelRefundNo());
            } else {
                log.info("[refund][processing] refundNo={} 渠道已受理，等待回调确认", refundNo);
            }
        } catch (RuntimeException e) {
            refundOrderService.markFailedIfProcessing(refundNo, abbreviate(e.getMessage()));
            log.error("[refund][failed] refundNo={}, afterSaleId={}, channel={}",
                    refundNo, refundOrder.getAfterSaleId(), refundOrder.getChannel(), e);
            throw e;
        }
        return refundOrderService.getByRefundNo(refundNo);
    }

    /** 按原支付渠道发起退款；余额渠道走 I41 余额入账（本地同步入账）。 */
    private ChannelRefundResult requestChannel(PayOrder payOrder, RefundOrder refundOrder,
                                               String reason) {
        PayChannelEnum channel = PayChannelEnum.of(refundOrder.getChannel());
        if (channel == PayChannelEnum.BALANCE) {
            BalanceRefundCO request = new BalanceRefundCO();
            request.setTransactionNo(refundOrder.getRefundNo());
            request.setUserId(payOrder.getBizUserId());
            request.setAmount(refundOrder.getRefundAmount());
            request.setBizNo(refundOrder.getRefundNo());
            balanceClient.refund(request);
            return ChannelRefundResult.settled(null);
        }
        return channelRouter.refund(channel, payOrder.getPayOrderNo(), refundOrder.getRefundNo(),
                refundOrder.getRefundAmount(), payOrder.getAmount(), reason);
    }

    /** 条件置成功后回写售后单（命中才回写，重复回调不重复回写）。 */
    private void markSuccessAndNotify(String refundNo, String channelRefundNo) {
        if (!refundOrderService.markSuccessIfProcessing(refundNo, channelRefundNo)) {
            log.info("[refund][duplicate] refundNo={} 非处理中，忽略", refundNo);
            return;
        }
        RefundOrder refundOrder = refundOrderService.getByRefundNo(refundNo);
        if (refundOrder != null) {
            notifyAfterSaleRefunded(refundOrder.getAfterSaleId());
        }
    }

    /**
     * 回写 trade 售后单为 REFUNDED（I42）。
     *
     * <p><b>fail-open</b>：状态回写丢失不阻断退款主流程（由对账/管理端重试收敛）；
     * {@code AfterSaleClientFallback} 本身即 fail-open，此处再加一层防御避免任何异常影响退款落账。
     */
    private void notifyAfterSaleRefunded(Long afterSaleId) {
        try {
            afterSaleClient.markRefunded(afterSaleId);
            log.info("退款成功，售后单已回写 REFUNDED: afterSaleId={}", afterSaleId);
        } catch (Exception e) {
            log.warn("退款成功但售后单回写失败（fail-open，待对账/重试收敛）: afterSaleId={}", afterSaleId, e);
        }
    }

    /** 入参防御（应用层）。 */
    private void validate(RefundCreateCO req) {
        if (req == null || req.getAfterSaleId() == null) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "售后单ID不能为空");
        }
        if (req.getOrderNo() == null || req.getOrderNo().isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "订单号不能为空");
        }
        if (req.getRefundAmount() == null || req.getRefundAmount() <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "退款金额必须大于0");
        }
    }

    /** 取支付单并做退款金额双保险校验（申请时已校验 ≤ payAmount）。 */
    private PayOrder requirePayOrder(RefundCreateCO req) {
        PayOrder payOrder = payOrderService.getByBizOrderNo(req.getOrderNo());
        if (payOrder == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在: " + req.getOrderNo());
        }
        if (payOrder.getAmount() == null || req.getRefundAmount() > payOrder.getAmount()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额超过实付金额");
        }
        if (payOrder.getBizUserId() == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单缺少支付用户: " + req.getOrderNo());
        }
        return payOrder;
    }

    /** 失败原因摘要：截断到列长上限内（不外泄渠道报文细节）。 */
    private String abbreviate(String message) {
        if (message == null || message.isBlank()) {
            return "渠道退款失败";
        }
        return message.length() <= MAX_FAIL_REASON_LENGTH ? message
                : message.substring(0, MAX_FAIL_REASON_LENGTH);
    }
}
