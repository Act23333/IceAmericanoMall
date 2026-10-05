package org.icedamericanomall.trade.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.trade.adapter.request.InvoiceApplyReq;
import org.icedamericanomall.trade.adapter.request.InvoiceHistoryReq;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.app.invoice.InvoiceApplyManager;
import org.icedamericanomall.trade.app.invoice.InvoiceBuyerManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家发票接口（F14 发票 INV1-INV5，契约 §4.15）。
 *
 * <p>控制器只做参数校验与委托：申请守卫（归属/状态/时间窗/一单一票）在
 * {@link InvoiceApplyManager}，查看/撤回/催开编排在 {@link InvoiceBuyerManager}；
 * VO 一律脱敏（税号/邮箱只回掩码，不含 cipher/hash 技术字段）。
 */
@RestController
@RequestMapping("/api/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceApplyManager invoiceApplyManager;
    private final InvoiceBuyerManager invoiceBuyerManager;

    /**
     * INV1 申请开票（含换开路径：已开具发票对同单重新申请 = 原票冲红 + 新票待开具）。
     *
     * <p>{@code @RateLimit} 防刷（PRD §15.8.4：INV1 限流，超限 FREQUENT_ERROR 429）。
     */
    @RateLimit(key = "ip", limit = 10, duration = 60)
    @PostMapping("/apply")
    public Result<InvoiceVO> apply(@Valid @RequestBody InvoiceApplyReq req) {
        return Result.ok(invoiceApplyManager.apply(UserContext.getUserId(), req));
    }

    /** INV2 我的发票分页（可按状态筛选；列表不含样票下载地址）。 */
    @GetMapping("/history")
    public Result<IPage<InvoiceVO>> history(@Valid InvoiceHistoryReq req) {
        return Result.ok(invoiceBuyerManager.pageMy(UserContext.getUserId(), req));
    }

    /** INV3 发票详情（仅本人；已开具含样票下载地址）。 */
    @GetMapping("/{invoiceNo}")
    public Result<InvoiceVO> detail(@PathVariable String invoiceNo) {
        return Result.ok(invoiceBuyerManager.detail(UserContext.getUserId(), invoiceNo));
    }

    /** INV4 买家撤回（仅待开具 1→4；已开具提示走换开）。 */
    @PutMapping("/{invoiceNo}/cancel")
    public Result<Void> cancel(@PathVariable String invoiceNo) {
        invoiceBuyerManager.cancel(UserContext.getUserId(), invoiceNo);
        return Result.ok();
    }

    /** INV5 买家催开（待开具满 7 天可催，24h 限频，O10）。 */
    @PutMapping("/{invoiceNo}/urge")
    public Result<Void> urge(@PathVariable String invoiceNo) {
        invoiceBuyerManager.urge(UserContext.getUserId(), invoiceNo);
        return Result.ok();
    }
}
