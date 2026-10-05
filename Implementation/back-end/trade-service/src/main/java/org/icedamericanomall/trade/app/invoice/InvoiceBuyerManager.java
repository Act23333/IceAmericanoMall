package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceHistoryReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.icedamericanomall.trade.domain.service.InvoicePolicy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 买家发票用例编排（应用层，INV2-INV5，F14 发票）。
 *
 * <pre>
 * INV2 GET  /api/invoice/history          —— 本人发票分页（可按状态筛选，脱敏）
 * INV3 GET  /api/invoice/{invoiceNo}      —— 发票详情（仅已开具附样票下载地址）
 * INV4 PUT  /api/invoice/{invoiceNo}/cancel —— 买家撤回（仅待开具 1→4）
 * INV5 PUT  /api/invoice/{invoiceNo}/urge   —— 买家催开（待开具满 7 天可催，24h 限频，O10）
 * </pre>
 *
 * <p><b>越权防护</b>：发票按业务键 {@code invoiceNo} 定位后强校验本人归属
 * （非本人 → FORBIDDEN，对齐 09-Constraints 越权红线）。
 * <b>催开触达</b>：O10 最小实现 = {@code urge_time} 记录 + 24h 限频 + 管理端催开排序视图，
 * 不新增主动触达管道（PRD §15.2.0 范围裁决）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceBuyerManager {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceIssuePort invoiceIssuePort;
    private final InvoiceAssembler invoiceAssembler;

    /**
     * INV2 本人发票分页（创建时间倒序）。
     *
     * <p>列表不做样票预签名（避免分页内 N 次对象存储往返）；下载地址由 INV3 详情按需解析。
     *
     * @param userId 买家 user_id
     * @param req    分页入参（status 可选筛选）
     * @return 脱敏分页
     */
    public IPage<InvoiceVO> pageMy(Long userId, InvoiceHistoryReq req) {
        return invoiceRepository.pageByBuyer(userId, req.getStatus(), req.getPage(), req.getSize())
                .convert(invoice -> invoiceAssembler.toVO(invoice, null));
    }

    /**
     * INV3 发票详情（仅本人；已开具发票解析样票下载地址）。
     *
     * @param userId    买家 user_id
     * @param invoiceNo 发票申请号
     * @return 脱敏 VO（已开具含 {@code pdfKey} 下载地址）
     * @throws BizException       发票不存在（NO_RESOURCE_FOUND）
     * @throws ForbiddenException 非本人发票
     */
    public InvoiceVO detail(Long userId, String invoiceNo) {
        Invoice invoice = requireOwnedInvoice(userId, invoiceNo);
        String downloadUrl = invoice.isIssued()
                ? invoiceIssuePort.presignDownloadUrl(invoice.getPdfKey())
                : null;
        return invoiceAssembler.toVO(invoice, downloadUrl);
    }

    /**
     * INV4 买家撤回（条件迁移 1→4；已开具提示走换开，终态不可撤回）。
     *
     * @param userId    买家 user_id
     * @param invoiceNo 发票申请号
     * @throws BizException       发票不存在 / 状态不支持撤回（含并发竞态 0 行回落）
     * @throws ForbiddenException 非本人发票
     */
    public void cancel(Long userId, String invoiceNo) {
        Invoice invoice = requireOwnedInvoice(userId, invoiceNo);
        invoice.assertBuyerCancellable();
        if (invoiceRepository.cancelIfPending(invoiceNo, null) == 0) {
            // 条件更新 0 行 = 并发窗口内状态已变更（先到先得），按守卫口径拒绝
            throw new BizException(ErrorCode.PARAM_ERROR, "当前发票状态不支持撤回");
        }
        log.info("[invoice-buyer][cancel] invoiceNo={} userId={} 撤回成功", invoiceNo, userId);
    }

    /**
     * INV5 买家催开（裁决映射见 {@link InvoicePolicy#checkUrge}；O10 = 仅记录 urge_time）。
     *
     * @param userId    买家 user_id
     * @param invoiceNo 发票申请号
     * @throws BizException 发票不存在 / 非待开具 / 未满 7 天 / 24h 限频（FREQUENT_ERROR）
     * @throws ForbiddenException 非本人发票
     */
    public void urge(Long userId, String invoiceNo) {
        Invoice invoice = requireOwnedInvoice(userId, invoiceNo);
        LocalDateTime now = LocalDateTime.now();
        switch (InvoicePolicy.checkUrge(invoice.getStatus(), invoice.getCreateTime(),
                invoice.getUrgeTime(), now)) {
            case ALLOWED -> {
                if (invoiceRepository.markUrged(invoiceNo, now) == 0) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "当前发票状态不支持催开");
                }
                log.info("[invoice-buyer][urge] invoiceNo={} userId={} 催开记录成功", invoiceNo, userId);
            }
            case NOT_PENDING -> throw new BizException(ErrorCode.PARAM_ERROR,
                    "当前发票状态不支持催开（已开具无需催开）");
            case TOO_EARLY -> throw new BizException(ErrorCode.PARAM_ERROR,
                    "发票开具处理中，待开具满 " + InvoicePolicy.URGE_MIN_PENDING_DAYS + " 天可催开");
            case RATE_LIMITED -> throw new BizException(ErrorCode.FREQUENT_ERROR,
                    "24 小时内已催开过，请稍后再试");
        }
    }

    /**
     * 按业务键加载发票并强校验本人归属（越权红线：非本人 → FORBIDDEN）。
     *
     * @throws BizException       发票不存在（NO_RESOURCE_FOUND）
     * @throws ForbiddenException 非本人发票
     */
    private Invoice requireOwnedInvoice(Long userId, String invoiceNo) {
        Invoice invoice = invoiceRepository.findByInvoiceNo(invoiceNo);
        if (invoice == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "发票不存在");
        }
        if (!invoice.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "仅本人可操作该发票");
        }
        return invoice;
    }
}
