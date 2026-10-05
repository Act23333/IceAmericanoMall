package org.icedamericanomall.trade.app.invoice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceApplyReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.enums.OrderStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
import org.icedamericanomall.trade.domain.repository.OrderRepository;
import org.icedamericanomall.trade.domain.service.InvoicePolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 发票申请用例编排（应用层，INV1 {@code POST /api/invoice/apply}，F14 发票）。
 *
 * <p><b>申请守卫（契约 §4.15，ADR-004）</b>：订单存在（{@code ORDER_NOT_FOUND}）→
 * 归属本人（{@code FORBIDDEN}）→ 仅「已完成」可申请 → 90 天窗内（起算
 * {@code orders.end_time}，决策 D6/O1）→ 一单一有效票预检 + {@code uk_order_active}
 * DuplicateKey 回落（AD51-53 先例）→ 企业抬头税号必填（聚合自封装守卫）。
 *
 * <p><b>换开语义（PRD D5，契约 §4.13.8e）</b>：买家对同单重新申请即换开——已有有效票为
 * 「已开具」时，原票冲红（让出有效槽位）+ 新票待开具（{@code red_for_id} 关联原票）
 * 在<b>同一本地事务</b>完成（守卫清单 #3）；有效票为「待开具」时拒绝（买家可先撤回）。
 *
 * <p><b>金额口径（决策 D3）</b>：开票金额 = 申请时 {@code payAmount} 快照，经
 * {@link InvoicePolicy#resolveInvoiceAmount} 按不变式 V2 对账后落库，此后不随订单变化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceApplyManager {

    /** 换开冲红留痕原因（原票 {@code red_reason}，换开前置冲红固定口径）。 */
    public static final String REASON_EXCHANGE = "买家换开发票（重新申请）";

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceTitleRepository invoiceTitleRepository;
    private final InvoiceAssembler invoiceAssembler;

    /**
     * 买家申请开票（含换开路径）。
     *
     * @param userId 买家 user_id（来自登录态，不信任报文）
     * @param req    申请入参（{@code titleId} 引用常用抬头或现填）
     * @return 新发票 VO（状态=待开具）
     * @throws BizException                            订单状态/时间窗/重复申请/抬头要素非法（PARAM_ERROR 等）
     * @throws org.icedamericanomall.common.exception.ForbiddenException 订单非本人
     */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceVO apply(Long userId, InvoiceApplyReq req) {
        Order order = requireInvoiceableOrder(userId, req.getOrderNo());
        InvoiceTitleSnapshot snapshot = resolveTitleSnapshot(userId, req);
        int amount = InvoicePolicy.resolveInvoiceAmount(order.getTotalAmount(),
                order.getDiscountAmount(), order.getPostFee(), order.getPayAmount());

        Invoice active = invoiceRepository.findActiveByOrderNo(order.getOrderNo());
        if (active != null && active.isPending()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该订单已申请过发票");
        }
        Long redForId = redForIdOf(active, order.getOrderNo());
        Invoice invoice = Invoice.create(order.getOrderNo(), userId, order.getSellerId(),
                snapshot.type(), snapshot.title(), snapshot.taxNo(), req.getEmail(), amount, redForId);
        return invoiceAssembler.toVO(saveOrDuplicate(invoice), null);
    }

    // ==================== 守卫与装配 ====================

    /**
     * 加载订单并执行申请前置守卫（存在 / 归属 / 已完成 / 90 天窗）。
     *
     * @throws BizException        订单不存在 / 非已完成 / 超窗
     * @throws ForbiddenException  非本人订单
     */
    private Order requireInvoiceableOrder(Long userId, String orderNo) {
        Order order = orderRepository.findOwnedOrder(orderNo, userId);
        if (OrderStatusEnum.COMPLETED.getCode() != order.getStatus()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单状态不支持开票（仅已完成订单可申请）");
        }
        if (!InvoicePolicy.withinApplyWindow(order.getEndTime(), LocalDateTime.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已超出开票时限（订单完成后 "
                    + InvoicePolicy.APPLY_WINDOW_DAYS + " 天内可申请）");
        }
        return order;
    }

    /**
     * 解析抬头快照来源（决策 O3）：{@code titleId} 引用常用抬头（类型/抬头/税号以抬头表为准，
     * 报文现填字段忽略）；否则现填（企业税号必填由聚合创建时自封装守卫兜底）。
     *
     * @throws BizException 引用的抬头不存在或非本人
     */
    private InvoiceTitleSnapshot resolveTitleSnapshot(Long userId, InvoiceApplyReq req) {
        if (req.getTitleId() == null) {
            return new InvoiceTitleSnapshot(req.getType(), req.getTitle(), req.getTaxNo());
        }
        InvoiceTitle title = invoiceTitleRepository.findByIdAndUserId(req.getTitleId(), userId);
        if (title == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "发票抬头不存在");
        }
        return new InvoiceTitleSnapshot(title.getType(), title.getTitle(),
                title.getTaxNo() == null ? null : title.getTaxNo().getPlain());
    }

    /**
     * 计算新票的 {@code red_for_id}：已有有效票为「已开具」→ 换开路径，先冲红原票
     * （条件迁移 2→3，让出 {@code uk_order_active} 槽位）并关联其 id；无有效票但存在
     * 历史终态票（管理端冲红后重开）→ 关联最近一张历史票 id；首次申请 → null。
     *
     * @param active  当前有效票（无有效票为 null）
     * @param orderNo 订单号
     * @return 新票 {@code red_for_id}
     */
    private Long redForIdOf(Invoice active, String orderNo) {
        if (active == null) {
            Invoice latest = invoiceRepository.findLatestByOrderNo(orderNo);
            return latest == null ? null : latest.getId();
        }
        int flushed = invoiceRepository.markRedAndExpire(active.getInvoiceNo(), REASON_EXCHANGE);
        if (flushed == 0) {
            // 先查后插与条件更新的竞态窗口：原票已被并发处理 → 以预检口径拒绝，不静默换开
            throw new BizException(ErrorCode.PARAM_ERROR, "该订单已申请过发票");
        }
        return active.getId();
    }

    /**
     * 落库（先查后插的 {@code uk_order_active} 并发兜底）：撞唯一键统一按
     * 「一单一有效票」拒绝（DuplicateKey 回落，AD51-53 先例）。
     */
    private Invoice saveOrDuplicate(Invoice invoice) {
        try {
            return invoiceRepository.save(invoice);
        } catch (DuplicateKeyException e) {
            log.info("[invoice-apply][duplicate] orderNo={} 命中 uk_order_active 兜底", invoice.getOrderNo());
            throw new BizException(ErrorCode.PARAM_ERROR, "该订单已申请过发票");
        }
    }

    /** 抬头快照（类型 / 抬头文本 / 税号明文——仅用于聚合内加密为三字段，不落日志）。 */
    private record InvoiceTitleSnapshot(Integer type, String title, String taxNo) {
    }
}
