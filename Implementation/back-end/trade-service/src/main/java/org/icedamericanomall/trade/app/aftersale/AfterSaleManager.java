package org.icedamericanomall.trade.app.aftersale;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.trade.app.TradeEventNotifier;
import org.icedamericanomall.trade.adapter.assembler.TradeConverter;
import org.icedamericanomall.trade.adapter.request.AfterSaleApplyReq;
import org.icedamericanomall.trade.adapter.response.AfterSaleVO;
import org.icedamericanomall.trade.domain.enums.AfterSaleStatusEnum;
import org.icedamericanomall.trade.domain.enums.AfterSaleTypeEnum;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.AfterSaleRepository;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 售后用例族编排（应用层）—— 申请 / 查询 / 审核 / 确认收货 / 退款回写。
 *
 * <p><b>退款触发时机（F1，V5.6 纠正，ADR-037 修订 ADR-035）</b>：
 * 仅退款（type=2）在审核同意（APPROVED）后立即触发退款；退货退款（type=1）在
 * 确认收到退货（RETURNED）后触发——货未退回不应放款。
 *
 * <p><b>事务纪律</b>：审核 / 确认退货落库（单条更新原子）<b>之后</b>，本编排器才在
 * 事务外调 {@link RefundManager#triggerRefund} 触发 pay-service 退款——Feign 严禁进事务
 * （硬约束），故「状态落库」与「触发退款」两层分离：落库失败不回滚退款，退款失败也不回滚
 * 落库（售后单停留 APPROVED/RETURNED，可由管理端重试收敛）。
 *
 * <pre>
 * Scenario: 用户提交售后申请
 *   Given 订单存在、归属当前用户、状态为待收货/已完成、退款金额不超过实付金额、无重复申请
 *   When 提交售后
 *   Then 落库一条"待审核"售后单
 * Scenario: 订单不存在 / 非本人订单 / 状态不允许 / 金额非法 → 拒绝
 * Scenario: 管理员审核非法状态值 → PARAM_ERROR
 *   And 已退款（REFUNDED，资金终态）/ 已退货（RETURNED，退款流程进行中）→ 不可再审核
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSaleManager {

    private final AfterSaleRepository afterSaleRepository;
    private final OrderRepository orderRepository;
    private final RefundManager refundManager;
    private final TradeConverter tradeConverter;
    private final TradeEventNotifier tradeEventNotifier;

    /** 用户提交售后申请（含重复/订单状态/金额校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleVO apply(Long userId, AfterSaleApplyReq req) {
        requireAfterSaleableOrder(userId, req);
        if (afterSaleRepository.existsByOrderNoAndUserId(req.getOrderNo(), userId)) {
            throw new BizException(ErrorCode.AFTER_SALE_EXISTS, "已有售后申请");
        }
        AfterSale afterSale = new AfterSale();
        afterSale.setUserId(userId);
        afterSale.setOrderNo(req.getOrderNo());
        afterSale.setType(req.getType());
        afterSale.setReason(req.getReason());
        afterSale.setRefundAmount(req.getRefundAmount());
        afterSale.setLogisticsNumber(req.getLogisticsNumber());
        afterSale.setStatus(AfterSaleStatusEnum.PENDING.getCode());
        return tradeConverter.toVO(afterSaleRepository.save(afterSale));
    }

    /** 我的售后单分页（创建时间倒序）。 */
    public IPage<AfterSaleVO> listMy(Long userId, int page, int size) {
        return afterSaleRepository.pageByUserId(userId, page, size).convert(tradeConverter::toVO);
    }

    /** 管理端售后单分页（可按状态过滤）。 */
    public IPage<AfterSaleVO> pageByStatus(Integer status, int page, int size) {
        return afterSaleRepository.pageByStatus(status, page, size).convert(tradeConverter::toVO);
    }

    /**
     * 售后审核；审核结果为「同意」<b>且为仅退款（type=2）</b>时，落库后触发退款（I40）。
     *
     * <p>退货退款（type=1）审核同意只落状态，退款待「确认收到退货」后触发（见 {@link #confirmReturn}）。
     * 退款发起失败会把异常上抛（fail-closed）：售后单已停留 APPROVED，管理端可见错误并重试。
     *
     * <p><b>审核禁改来源（ADR-037 风险收敛）</b>：已退款（REFUNDED，资金终态）与已退货
     * （RETURNED，退款流程进行中）均不可再被审核改回其它状态——RETURNED 之后的唯一合法去向是
     * 退款回调回写 REFUNDED（{@link #markRefunded}），管理端审核不得扰动该状态语义。
     *
     * @param id     售后单 ID
     * @param status 目标状态（2-同意时按类型决定是否触发退款）
     * @param remark 审核备注
     */
    public void review(Long id, Integer status, String remark) {
        AfterSaleStatusEnum target = AfterSaleStatusEnum.of(status);
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法售后状态: " + status);
        }
        AfterSale entity = requireAfterSale(id, ErrorCode.APPLICATION_NOT_FOUND, "售后申请不存在");
        // V5.5 资金线守卫：已退款是资金终态，禁止再被审核改回其它状态（否则等于「撤销退款」）
        if (AfterSaleStatusEnum.REFUNDED.getCode() == entity.getStatus()) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID, "售后单已退款，不可再审核");
        }
        // ADR-037 风险收敛：已退货即退货退款流程进行中，审核窗口已关闭，仅剩退款回调回写 REFUNDED
        if (AfterSaleStatusEnum.RETURNED.getCode() == entity.getStatus()) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID,
                    "售后单已确认退货、退款流程进行中，不可再审核");
        }
        entity.setStatus(target.getCode());
        entity.setAdminRemark(remark);
        afterSaleRepository.update(entity);

        // 买家触达（ADR-042）：审核结果站内信（本方法无事务，落库成功后 fail-open 发送）
        tradeEventNotifier.notifyAfterSaleReviewed(entity, remark);

        // 仅退款（type=2）在审核同意时立即退款；退货退款待「确认收到退货」后再退款
        if (target == AfterSaleStatusEnum.APPROVED
                && AfterSaleTypeEnum.of(entity.getType()) == AfterSaleTypeEnum.REFUND_ONLY) {
            refundManager.triggerRefund(id);
        }
    }

    /**
     * 确认收到退货（V5.6 新增，退货退款的退款触发点）—— 仅退货退款（type=1）适用。
     *
     * <p>状态迁移 {@code APPROVED(2) → RETURNED(4)} 走条件更新（本地原子），随后在事务外
     * 触发退款（I40）。已 REFUNDED 的重复触发被状态守卫/条件迁移挡住，
     * 资金侧另有 {@code uk_after_sale} 兜底幂等。
     *
     * @param id 售后单 ID
     */
    public void confirmReturn(Long id) {
        AfterSale entity = requireAfterSale(id, ErrorCode.NO_RESOURCE_FOUND, "售后申请不存在");
        if (AfterSaleTypeEnum.of(entity.getType()) != AfterSaleTypeEnum.RETURN_REFUND) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅退货退款售后可确认收到退货");
        }
        if (AfterSaleStatusEnum.REFUNDED.getCode() == entity.getStatus()) {
            throw new BizException(ErrorCode.REFUND_STATUS_INVALID, "售后单已退款，不可确认退货");
        }
        if (AfterSaleStatusEnum.RETURNED.getCode() == entity.getStatus()) {
            // 幂等：重复确认收到退货视为成功；退款触发照常发起，由 pay-service 的 uk_after_sale 兜底
            log.info("售后单已处于已退货状态，确认退货幂等空操作: id={}", id);
        } else {
            int updated = afterSaleRepository.updateStatusConditionally(id,
                    AfterSaleStatusEnum.APPROVED.getCode(), AfterSaleStatusEnum.RETURNED.getCode());
            if (updated == 0) {
                throw new BizException(ErrorCode.REFUND_STATUS_INVALID,
                        "售后单状态不支持确认收到退货: id=" + id);
            }
        }
        refundManager.triggerRefund(id);
    }

    /**
     * 退款成功回写：{@code APPROVED(2) | RETURNED(4) → REFUNDED(5)} 条件迁移（I42）。
     *
     * <p>两条来源边：仅退款（审核同意后）与退货退款（确认收到退货后）——共用同一资金终态。
     * 幂等：状态不属于任一来源（已迁移 / 其它状态）时为空操作，不抛异常。
     */
    public void markRefunded(Long id) {
        AfterSale entity = requireAfterSale(id, ErrorCode.NO_RESOURCE_FOUND, "售后申请不存在");
        int updated = afterSaleRepository.updateStatusConditionallyFrom(id,
                List.of(AfterSaleStatusEnum.APPROVED.getCode(), AfterSaleStatusEnum.RETURNED.getCode()),
                AfterSaleStatusEnum.REFUNDED.getCode());
        if (updated == 0) {
            // 幂等空操作：状态已迁移（重复回调）或非 APPROVED/RETURNED（退款处理中/失败时停留原态）
            log.info("售后单非 APPROVED/RETURNED，退款成功回写为空操作（幂等）: id={}, currentStatus={}",
                    id, entity.getStatus());
        }
    }

    // ==================== 内部方法 ====================

    /** 取售后单；不存在抛指定错误码。 */
    private AfterSale requireAfterSale(Long id, ErrorCode notFoundCode, String message) {
        AfterSale entity = afterSaleRepository.findById(id);
        if (entity == null) {
            throw new BizException(notFoundCode, message);
        }
        return entity;
    }

    /** 校验订单存在、归属当前用户、状态允许售后、退款金额不超过实付金额、售后类型合法。 */
    private void requireAfterSaleableOrder(Long userId, AfterSaleApplyReq req) {
        if (req.getOrderNo() == null || req.getOrderNo().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单号不能为空");
        }
        // V5.4: 类型枚举校验（适配层 @Range 已拦一层，此处为领域不变量兜底，覆盖非 HTTP 调用方）
        if (AfterSaleTypeEnum.of(req.getType()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "售后类型非法: " + req.getType());
        }
        Order order = orderRepository.findByOrderNo(req.getOrderNo());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权对该订单申请售后");
        }
        int status = order.getStatus();
        if (status != OrderStatusEnum.PENDING_RECEIPT.getCode()
                && status != OrderStatusEnum.COMPLETED.getCode()) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待收货/已完成订单可申请售后");
        }
        int payable = order.getPayAmount() != null ? order.getPayAmount() : 0;
        Integer refund = req.getRefundAmount();
        if (refund == null || refund <= 0 || refund > payable) {
            throw new BizException(ErrorCode.PARAM_ERROR, "退款金额非法，需大于 0 且不超过实付金额");
        }
    }
}
