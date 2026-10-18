package org.icedamericanomall.trade.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.trade.adapter.request.AdminInvoicePageReq;
import org.icedamericanomall.trade.adapter.request.InvoiceRedFlushReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.invoice.InvoiceAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台后台发票接口（F14 发票管理端 AD57-AD60，契约 §4.13.8e）。
 *
 * <p>控制器只做参数校验与委托：开具/作废/冲红编排与状态机守卫在
 * {@link InvoiceAdminManager}。台账导出延 Phase 2（O7 范围裁决）。
 *
 * <p><b>审计（ADR-032 管道一）</b>：三个写操作均标注 {@code @OperationLog}（照
 * AdminPromotionController 先例），产出单行 JSON 审计日志（logger {@code AUDIT}）；
 * 请求体含冲红原因，默认不记参数值（PII 安全默认）。
 */
@RestController
@RequestMapping("/api/admin/invoice")
@RequiredArgsConstructor
public class AdminInvoiceController {

    /** 审计模块：交易域发票。 */
    private static final String MODULE_TRADE_INVOICE = "TRADE_INVOICE";

    /** 审计动作：模拟开具发票。 */
    private static final String ACTION_ISSUE_INVOICE = "ISSUE_INVOICE";

    /** 审计动作：作废发票。 */
    private static final String ACTION_VOID_INVOICE = "VOID_INVOICE";

    /** 审计动作：冲红发票。 */
    private static final String ACTION_RED_FLUSH_INVOICE = "RED_FLUSH_INVOICE";

    private final InvoiceAdminManager invoiceAdminManager;

    /** AD57：全平台发票分页（状态/商家/部分退款筛选 + urgeOnly 催开排序视图，O10）。 */
    @PreAuthorize("@ss.hasPermi('invoice:read')")
    @GetMapping("/page")
    public Result<IPage<InvoiceVO>> page(@Valid AdminInvoicePageReq req) {
        return Result.ok(invoiceAdminManager.page(req));
    }

    /** AD58：模拟开具（1→2：模拟票号 + 样票 PDF 上传 MinIO；开具成功站内信触达买家）。 */
    @OperationLog(module = MODULE_TRADE_INVOICE, action = ACTION_ISSUE_INVOICE, description = "模拟开具发票")
    // 开票 —— 与作废/冲红**分开授权**（Q2）。SoD：开票员不持 invoice:void（见草案 §6.1），
    // 即"能开不能废"，避免一人闭环；该约束目前是**授权约定**，系统未强制（SoD 另立项）。
    @PreAuthorize("@ss.hasPermi('invoice:issue')")
    @PutMapping("/{invoiceNo}/issue")
    public Result<InvoiceVO> issue(@PathVariable String invoiceNo) {
        return Result.ok(invoiceAdminManager.issue(invoiceNo));
    }

    /** AD59：作废（仅待开具 1→4；已开具拒绝并提示走冲红，合规守卫）。 */
    @OperationLog(module = MODULE_TRADE_INVOICE, action = ACTION_VOID_INVOICE, description = "作废发票")
    @PreAuthorize("@ss.hasPermi('invoice:void')")
    @PutMapping("/{invoiceNo}/void")
    public Result<Void> voidInvoice(@PathVariable String invoiceNo) {
        invoiceAdminManager.voidInvoice(invoiceNo);
        return Result.ok();
    }

    /** AD60：冲红（仅已开具 2→3，原因必填；重复冲红幂等空操作）。 */
    @OperationLog(module = MODULE_TRADE_INVOICE, action = ACTION_RED_FLUSH_INVOICE, description = "冲红发票")
    // 冲红 —— **挂 invoice:void**（Q9 裁决）：冲红只做"作废原票"，不重开新票，
    // 语义上属于作废动作的子集，故复用 invoice:void 而不新造 invoice:red-flush。
    @PreAuthorize("@ss.hasPermi('invoice:void')")
    @PutMapping("/{invoiceNo}/red-flush")
    public Result<Void> redFlush(@PathVariable String invoiceNo,
                                 @RequestBody @Valid InvoiceRedFlushReq req) {
        invoiceAdminManager.redFlush(invoiceNo, req.getReason());
        return Result.ok();
    }
}
