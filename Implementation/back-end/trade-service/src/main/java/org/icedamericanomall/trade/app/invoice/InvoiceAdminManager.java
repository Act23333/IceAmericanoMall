package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.AdminInvoicePageReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.TradeEventNotifier;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.port.InvoiceIssueParam;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.port.InvoiceIssueResult;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 发票管理端用例编排（应用层，AD57-AD60 + I56 联动，F14 发票）。
 *
 * <pre>
 * AD57 GET /api/admin/invoice/page                —— 全平台分页（状态/商家/部分退款筛选 + 催开排序）
 * AD58 PUT /api/admin/invoice/{no}/issue          —— 模拟开具（1→2，模拟票号 + 样票 PDF）
 * AD59 PUT /api/admin/invoice/{no}/void           —— 作废（仅待开具 1→4；已开具拒绝走冲红）
 * AD60 PUT /api/admin/invoice/{no}/red-flush      —— 冲红（仅已开具 2→3，原因必填；重复幂等）
 * I56  POST /internal/trade/invoice/void-on-full-refund —— 全额退款联动（待开具→作废 / 已开具→冲红）
 * I60  POST /internal/trade/invoice/partial-refund-flag —— 部分退款置标（已开具有效票置标记待人工）
 * </pre>
 *
 * <p><b>事务纪律</b>：AD58 开具含样票 PDF 生成 + 对象存储上传（耗时外部操作），
 * <b>不开启事务</b>——落库仅为单条条件更新（自身原子），开具失败不落状态可直接重试；
 * 触达（站内信 Feign）一律在条件更新落库之后（Feign 严禁进事务，硬约束）。
 *
 * <p><b>I56 幂等语义</b>：无有效票 / 已终态 = 空操作成功返回（仓储内部条件更新先到先得）；
 * 原因 = 系统原因码（含退款幂等键 {@code sceneRef} 审计追溯）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceAdminManager {

    /** 管理端作废留痕原因（AD59 无请求体，固定口径）。 */
    public static final String REASON_ADMIN_VOID = "管理端作废";

    /** 全额退款自动联动系统原因码前缀（后接退款幂等键 sceneRef）。 */
    public static final String SYSTEM_REASON_FULL_REFUND_PREFIX = "SYSTEM_FULL_REFUND:";

    /** 全额退款联动结果：已自动作废（对应状态 4）。 */
    private static final int REFUND_OUTCOME_VOIDED = 4;

    /** 全额退款联动结果：已自动冲红（对应状态 3）。 */
    private static final int REFUND_OUTCOME_RED_FLUSHED = 3;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceIssuePort invoiceIssuePort;
    private final InvoiceAssembler invoiceAssembler;
    private final TradeEventNotifier tradeEventNotifier;

    /**
     * AD57 管理端发票分页（创建时间倒序；{@code urgeOnly} 时仅看有催开记录并按 urge_time 倒序）。
     *
     * @param req 筛选入参
     * @return 脱敏分页（列表不做样票预签名）
     */
    public IPage<InvoiceVO> page(AdminInvoicePageReq req) {
        return invoiceRepository.pageForAdmin(req.getStatus(), req.getSellerId(),
                        req.getPartialRefund(), Boolean.TRUE.equals(req.getUrgeOnly()),
                        req.getPage(), req.getSize())
                .convert(invoice -> invoiceAssembler.toVO(invoice, null));
    }

    /**
     * AD58 模拟开具（条件迁移 1→2）：聚合守卫 → 防腐端口开具（模拟票号 + 样票上传）→
     * 条件更新回填（0 行 = 并发竞态，按状态冲突拒绝）→ 事务外触达买家。
     *
     * @param invoiceNo 发票申请号
     * @return 已开具发票 VO（含模拟票号与样票下载地址）
     * @throws BizException 发票不存在 / 非待开具 / 样票上传失败 / 并发状态变更
     */
    public InvoiceVO issue(String invoiceNo) {
        Invoice invoice = requireInvoice(invoiceNo);
        invoice.assertIssueable();
        InvoiceIssueResult result = invoiceIssuePort.issue(toIssueParam(invoice));
        LocalDateTime issueTime = LocalDateTime.now();
        if (invoiceRepository.issueIfPending(invoiceNo, result.mockInvoiceNo(),
                result.pdfKey(), issueTime) == 0) {
            // 先查后开的竞态窗口：待开具已被并发处理（撤回/他端开具）→ 拒绝，样票留档无害（模拟环境）
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待开具发票可开具，当前状态已变更");
        }
        stampIssued(invoice, result, issueTime);
        log.info("[invoice-admin][issue] invoiceNo={} mockInvoiceNo={} 开具成功", invoiceNo, result.mockInvoiceNo());
        tradeEventNotifier.notifyInvoiceIssued(invoice);
        return invoiceAssembler.toVO(invoice, invoiceIssuePort.presignDownloadUrl(invoice.getPdfKey()));
    }

    /**
     * AD59 作废（条件迁移 1→4；已开具发票由聚合合规守卫拒绝，PRD 守卫清单 #1）。
     *
     * @param invoiceNo 发票申请号
     * @throws BizException 发票不存在 / 已开具（提示走冲红）/ 状态冲突
     */
    public void voidInvoice(String invoiceNo) {
        Invoice invoice = requireInvoice(invoiceNo);
        invoice.assertVoidable();
        if (invoiceRepository.cancelIfPending(invoiceNo, REASON_ADMIN_VOID) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前发票状态不支持作废");
        }
        invoice.setStatus(InvoiceStatusEnum.VOIDED.getCode());
        invoice.setRedReason(REASON_ADMIN_VOID);
        log.info("[invoice-admin][void] invoiceNo={} 作废成功", invoiceNo);
        tradeEventNotifier.notifyInvoiceVoided(invoice, REASON_ADMIN_VOID);
    }

    /**
     * AD60 冲红（条件迁移 2→3，原因必填留痕）：已冲红重复触发 = 幂等空操作
     * （PRD US-INV-12）；其余非已开具状态拒绝。
     *
     * @param invoiceNo 发票申请号
     * @param reason    冲红原因（人工必填）
     * @throws BizException 发票不存在 / 仅已开具可冲红 / 并发状态变更
     *
     * <p><b>语义（2026-10-18 实测后改写，裁决 A）</b>：本方法<b>只把原票置为已冲红（RED_FLUSHED）</b>，
     * 写 {@code red_reason} 追溯，并触达买家 —— <b>不重开新票、也不生成红字票记录</b>。
     * 因此它是<b>单条条件更新</b>（{@code redFlushIfIssued}），天然原子，<b>不存在"作废成功、重开失败"的中间态</b>。
     *
     * <p>先前 Q9 裁决里"冲红 = 作废 + 重开"的表述与代码不符，已按实际行为改正；
     * 权限位结论不变（仍挂 {@code invoice:void}）—— 理由改为「只做作废，{@code invoice:void} 已覆盖」。
     *
     * <p><b>登记（非缺陷定论）</b>：红字票是"系统外开具"还是"本系统应生成"尚待业务确认；
     * {@code red_reason} 目前是自由文本，<b>不承载红字票号</b>，无法据此追溯红字票。
     */
    public void redFlush(String invoiceNo, String reason) {
        Invoice invoice = requireInvoice(invoiceNo);
        if (InvoiceStatusEnum.RED_FLUSHED.getCode() == invoice.getStatus()) {
            log.info("[invoice-admin][red-flush] invoiceNo={} 已冲红，幂等空操作", invoiceNo);
            return;
        }
        invoice.assertRedFlushable();
        if (invoiceRepository.redFlushIfIssued(invoiceNo, reason) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已开具发票可冲红，当前状态已变更");
        }
        invoice.setStatus(InvoiceStatusEnum.RED_FLUSHED.getCode());
        invoice.setRedReason(reason);
        log.info("[invoice-admin][red-flush] invoiceNo={} reason={} 冲红成功", invoiceNo, reason);
        tradeEventNotifier.notifyInvoiceRedFlushed(invoice, reason);
    }

    /**
     * I56 全额退款自动联动（pay-service 退款资金终态触发，决策 D4）：
     * 待开具 → 自动作废(4)、已开具 → 自动冲红(3)，原因 = 系统原因码；
     * 无有效票 / 已终态 = 幂等空操作（不触达、不告警）。
     *
     * <p>部分退款<b>不</b>触发本联动（调用方按全额判定，PRD D4 Phase 1 口径）。
     *
     * @param orderNo  订单号
     * @param sceneRef 退款幂等键（refund_order.biz_ref，审计追溯）
     */
    public void voidOnFullRefund(String orderNo, String sceneRef) {
        String systemReason = SYSTEM_REASON_FULL_REFUND_PREFIX + sceneRef;
        int outcome = invoiceRepository.voidOnFullRefund(orderNo, systemReason);
        if (outcome == 0) {
            log.info("[invoice-admin][refund-link] orderNo={} 无有效票，幂等空操作", orderNo);
            return;
        }
        Invoice invoice = invoiceRepository.findLatestByOrderNo(orderNo);
        if (outcome == REFUND_OUTCOME_VOIDED) {
            log.info("[invoice-admin][refund-link] orderNo={} 待开具发票已自动作废", orderNo);
            tradeEventNotifier.notifyInvoiceVoided(invoice, systemReason);
        } else if (outcome == REFUND_OUTCOME_RED_FLUSHED) {
            log.info("[invoice-admin][refund-link] orderNo={} 已开具发票已自动冲红", orderNo);
            tradeEventNotifier.notifyInvoiceRedFlushed(invoice, systemReason);
        }
    }

    /**
     * I60 部分退款发票置标（pay-service 部分退款触发，决策 D4/O6）：
     * 已开具有效票置 {@code partial_refund_flag=1} 待人工处理（不改状态、不冲红、不触达——
     * 退款成功站内信由 pay 侧发送）。
     *
     * <p>口径（PRD US-INV-8 / 契约 §4.14.16c）：<b>待开具票遇部分退款无需标记</b>
     * （开具后人工冲红口径不受影响）；已置标重复触发 / 终态票 / 无有效票 → 幂等空操作。
     * {@code sceneRef} 仅审计日志留痕（表无独立落库列，置标本身以 {@code partial_refund_flag=1}
     * 为幂等水位）。
     *
     * @param orderNo  订单号
     * @param sceneRef 退款幂等键（refund_order.biz_ref，审计追溯）
     */
    public void markPartialRefundFlag(String orderNo, String sceneRef) {
        int marked = invoiceRepository.markPartialRefundFlagIfIssued(orderNo);
        if (marked == 0) {
            log.info("[invoice-admin][partial-refund] orderNo={} sceneRef={} "
                    + "无已开具有效票或已置标，幂等空操作", orderNo, sceneRef);
            return;
        }
        log.info("[invoice-admin][partial-refund] orderNo={} sceneRef={} "
                + "已置 partial_refund_flag=1 待人工处理", orderNo, sceneRef);
    }

    // ==================== 内部装配 ====================

    /** 按业务键加载发票（不存在 → NO_RESOURCE_FOUND）。 */
    private Invoice requireInvoice(String invoiceNo) {
        Invoice invoice = invoiceRepository.findByInvoiceNo(invoiceNo);
        if (invoice == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "发票不存在");
        }
        return invoice;
    }

    /**
     * 聚合 → 开具端口入参。税号/邮箱为解密后明文，仅用于样票票面渲染（内存流转，
     * 禁止落日志——09-Constraints 脱敏红线）。
     */
    private InvoiceIssueParam toIssueParam(Invoice invoice) {
        return new InvoiceIssueParam(invoice.getInvoiceNo(), invoice.getOrderNo(),
                invoice.getSellerId(), invoice.getType(), invoice.getTitle(),
                invoice.getTaxNo() == null ? null : invoice.getTaxNo().getPlain(),
                invoice.getEmail() == null ? null : invoice.getEmail().getPlain(),
                invoice.getAmount());
    }

    /** 开具回填聚合状态（供 VO 装配与触达使用）。 */
    private void stampIssued(Invoice invoice, InvoiceIssueResult result, LocalDateTime issueTime) {
        invoice.setStatus(InvoiceStatusEnum.ISSUED.getCode());
        invoice.setMockInvoiceNo(result.mockInvoiceNo());
        invoice.setPdfKey(result.pdfKey());
        invoice.setIssueTime(issueTime);
    }
}
