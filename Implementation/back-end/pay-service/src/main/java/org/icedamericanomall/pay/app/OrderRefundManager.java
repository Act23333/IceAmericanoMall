package org.icedamericanomall.pay.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.OrderRefundCO;
import org.icedamericanomall.dto.RefundResultCO;
import org.icedamericanomall.pay.adapter.assembler.RefundConverter;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.domain.model.refundorder.RefundOrder;
import org.icedamericanomall.pay.domain.repository.PayOrderRepository;
import org.icedamericanomall.pay.domain.repository.RefundOrderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 无售后单退款编排 Manager（I55 按订单全额退款，F13 拼团）—— 独立用例族（ADR-025），
 * 与 {@link RefundManager}（售后退款）并列，经「读支付单取渠道/金额」单向依赖支付单聚合。
 *
 * <p><b>职责边界（ADR-049 幂等锚点泛化）</b>：无售后单的退款命令没有 {@code afterSaleId}，
 * 幂等锚点改用 {@code refund_order.uk_biz_ref}（{@code sceneRef}，拼团场景 = {@code GB:{orderNo}}，
 * 由调用方 trade-service 构造）；退款金额恒为支付单<b>全额</b>（拼团失败 = 整单退）。
 * 渠道执行 / 条件状态机 / 失败落账全部复用 {@link RefundManager} 的共享执行入口
 * {@link RefundManager#execute}（不复制粘贴）；退款成功触达复用 {@link PayEventNotifier}
 * （afterSaleId=null 自动跳过 I42 回写，REFUND_SUCCESS 站内信照常，ADR-042）。
 *
 * <p><b>事务纪律</b>：与 {@link RefundManager} 一致 —— 渠道调用（Wallet/微信/支付宝）是 Feign，
 * 不加 {@code @Transactional}；需要事务的写入下沉为仓储条件更新。
 *
 * <pre>
 * Scenario: 拼团参团失败/结团失败 → trade 调 I55
 *   Then 按支付单全额建「处理中」退款单（biz_ref=sceneRef）→ 复用 RefundManager 执行入口
 * Scenario: 同 sceneRef 重放（幂等）
 *   Then 返回既有退款单，不新建、不再发起渠道退款（uk_biz_ref 兜底）
 * Scenario: 并发同 sceneRef 建单
 *   Then DuplicateKeyException 回落为返回既有退款单
 * Scenario: 支付单不存在
 *   Then ORDER_NOT_FOUND 原始透出（ADR-004，fail-closed，ADR-035 §5 定类不变）
 * Scenario: 余额渠道
 *   Then I41 同步入账 → 同步置成功；微信渠道 → 处理中，等 PAY6 回调
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRefundManager {

    private final RefundOrderRepository refundOrderService;
    private final PayOrderRepository payOrderService;
    private final RefundManager refundManager;
    private final RefundConverter refundConverter;

    /**
     * 按订单全额退款（I55）—— {@code sceneRef} 幂等。
     *
     * @param cmd 无售后单退款命令（orderNo / sceneRef / reason 可选摘要）
     * @return I55 内部契约（字段子集：refundNo + status）
     * @throws BadRequestException 入参非法（{@code PARAM_ERROR}）
     * @throws BizException        支付单不存在（{@code ORDER_NOT_FOUND}）/ 渠道退款失败
     *                             （原异常透出，退款单已置失败，AD43 可重试）
     */
    public RefundResultCO refundByOrder(OrderRefundCO cmd) {
        validate(cmd);
        RefundOrder existing = refundOrderService.getByBizRef(cmd.getSceneRef());
        if (existing != null) {
            log.info("[order-refund][idempotent] sceneRef={} 已有退款单，返回既有: refundNo={}, status={}",
                    cmd.getSceneRef(), existing.getRefundNo(), existing.getStatus());
            return refundConverter.toResultCO(existing);
        }
        PayOrder payOrder = requirePayOrder(cmd);
        RefundOrder refundOrder;
        try {
            refundOrder = refundOrderService.createOrderRefundProcessing(cmd.getOrderNo(),
                    payOrder.getPayOrderNo(), payOrder.getBizUserId(), payOrder.getPayChannelCode(),
                    payOrder.getAmount(), cmd.getSceneRef());
        } catch (DuplicateKeyException e) {
            // 并发孪生请求已建单：回落为「返回既有退款单」（uk_biz_ref 兜底）
            RefundOrder concurrent = refundOrderService.getByBizRef(cmd.getSceneRef());
            if (concurrent == null) {
                throw e;
            }
            log.info("[order-refund][idempotent][concurrent] sceneRef={} 并发建单，返回既有: refundNo={}",
                    cmd.getSceneRef(), concurrent.getRefundNo());
            return refundConverter.toResultCO(concurrent);
        }
        log.info("[order-refund][created] sceneRef={} refundNo={} 全额退款 amount={}",
                cmd.getSceneRef(), refundOrder.getRefundNo(), payOrder.getAmount());
        return refundConverter.toResultCO(refundManager.execute(refundOrder, payOrder, cmd.getReason()));
    }

    /** 入参防御（应用层）：Bean Validation 之外的双保险（内部直调等路径兜底）。 */
    private void validate(OrderRefundCO cmd) {
        if (cmd == null || isBlank(cmd.getOrderNo())) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "订单号不能为空");
        }
        if (isBlank(cmd.getSceneRef())) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "幂等业务键不能为空");
        }
    }

    /** 取支付单（退款金额 = 支付单全额，ADR-049）；用户缺失无法原路退回，同口径拒绝。 */
    private PayOrder requirePayOrder(OrderRefundCO cmd) {
        PayOrder payOrder = payOrderService.getByBizOrderNo(cmd.getOrderNo());
        if (payOrder == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单不存在: " + cmd.getOrderNo());
        }
        if (payOrder.getAmount() == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单金额缺失: " + cmd.getOrderNo());
        }
        if (payOrder.getBizUserId() == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "支付单缺少支付用户: " + cmd.getOrderNo());
        }
        return payOrder;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
