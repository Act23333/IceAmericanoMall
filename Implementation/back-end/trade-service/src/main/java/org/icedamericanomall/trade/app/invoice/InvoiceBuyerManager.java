package org.icedamericanomall.trade.app.invoice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.trade.adapter.assembler.InvoiceAssembler;
import org.icedamericanomall.trade.adapter.request.InvoiceHistoryReq;
import org.icedamericanomall.trade.adapter.request.InvoiceTitleReq;
import org.icedamericanomall.trade.adapter.response.InvoiceTitleVO;
import org.icedamericanomall.trade.adapter.response.InvoiceVO;
import org.icedamericanomall.trade.domain.model.invoice.Invoice;
import org.icedamericanomall.trade.domain.model.invoice.InvoiceTitle;
import org.icedamericanomall.trade.domain.port.InvoiceIssuePort;
import org.icedamericanomall.trade.domain.repository.InvoiceRepository;
import org.icedamericanomall.trade.domain.repository.InvoiceTitleRepository;
import org.icedamericanomall.trade.domain.service.InvoicePolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 买家发票用例编排（应用层，INV2-INV9，F14 发票）。
 *
 * <pre>
 * INV2 GET  /api/invoice/history            —— 本人发票分页（可按状态筛选，脱敏）
 * INV3 GET  /api/invoice/{invoiceNo}        —— 发票详情（仅已开具附样票下载地址）
 * INV4 PUT  /api/invoice/{invoiceNo}/cancel —— 买家撤回（仅待开具 1→4）
 * INV5 PUT  /api/invoice/{invoiceNo}/urge   —— 买家催开（待开具满 7 天可催，24h 限频，O10）
 * INV6 GET  /api/invoice/title/list         —— 常用抬头列表（默认在前，税号只出掩码）
 * INV7 POST /api/invoice/title              —— 新建常用抬头（isDefault=true 先清旧默认）
 * INV8 PUT  /api/invoice/title/{id}         —— 编辑常用抬头（全量更新，归属校验 403/1103）
 * INV9 DELETE /api/invoice/title/{id}       —— 删除常用抬头（软删，默认不自动递补）
 * </pre>
 *
 * <p><b>越权防护</b>：发票按业务键 {@code invoiceNo} 定位后强校验本人归属
 * （非本人 → FORBIDDEN，对齐 09-Constraints 越权红线）；抬头按 id 定位后同样强校验。
 * <b>催开触达</b>：O10 最小实现 = {@code urge_time} 记录 + 24h 限频 + 管理端催开排序视图，
 * 不新增主动触达管道（PRD §15.2.0 范围裁决）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceBuyerManager {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceTitleRepository invoiceTitleRepository;
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
     * INV6 常用抬头列表（默认在前，组内保持仓储的创建时间倒序；税号只出掩码）。
     *
     * @param userId 买家 user_id
     * @return 脱敏抬头列表
     */
    public List<InvoiceTitleVO> listTitles(Long userId) {
        List<InvoiceTitle> titles = new ArrayList<>(invoiceTitleRepository.listByUserId(userId));
        // 稳定排序：默认在前，组内保持 listByUserId 的创建时间倒序（契约 INV6 口径）
        titles.sort(Comparator.comparing(InvoiceTitle::isDefaultTitle).reversed());
        return titles.stream().map(invoiceAssembler::toTitleVO).toList();
    }

    /**
     * INV7 新建常用抬头（企业税号必填由聚合自封装守卫兜底；税号 PII 三字段加密）。
     *
     * <p>{@code isDefault=true} 时先清本人旧默认（{@code uk_user_default} 多 NULL 唯一语义），
     * 清旧与插入同一本地事务（并发窗口由唯一键兜底）。
     *
     * @param userId 买家 user_id（来自登录态，不信任报文）
     * @param req    抬头入参
     * @return 新抬头 VO（税号只回掩码）
     * @throws BizException 类型非法 / 抬头缺失或超长 / 企业缺税号（PARAM_ERROR）
     */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceTitleVO createTitle(Long userId, InvoiceTitleReq req) {
        InvoiceTitle title = InvoiceTitle.create(userId, req.getType(), req.getTitle(),
                req.getTaxNo(), Boolean.TRUE.equals(req.getIsDefault()));
        if (title.isDefaultTitle()) {
            invoiceTitleRepository.clearDefaultFlag(userId);
        }
        return invoiceAssembler.toTitleVO(invoiceTitleRepository.save(title));
    }

    /**
     * INV8 编辑常用抬头（全量更新语义：类型/抬头/税号/默认标记整体替换）。
     *
     * <p>归属校验先行（不存在 → 1103；非本人 → FORBIDDEN 越权红线）。换设默认先清旧默认；
     * 税号三字段与默认标记允许置 NULL 清列（显式 SQL {@code updateFull}——
     * MP {@code updateById} 忽略空值，不满足「企业转个人清税号」语义）。
     *
     * @param userId 买家 user_id
     * @param id     抬头 id
     * @param req    抬头入参
     * @throws BizException       抬头不存在 / 抬头要素非法 / 并发已软删
     * @throws ForbiddenException 非本人抬头
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(Long userId, Long id, InvoiceTitleReq req) {
        InvoiceTitle title = requireOwnedTitle(userId, id);
        boolean wasDefault = title.isDefaultTitle();
        title.applyFullUpdate(req.getType(), req.getTitle(), req.getTaxNo(),
                Boolean.TRUE.equals(req.getIsDefault()));
        if (title.isDefaultTitle() && !wasDefault) {
            invoiceTitleRepository.clearDefaultFlag(userId);
        }
        if (invoiceTitleRepository.updateFull(title) == 0) {
            // 加载与更新之间被并发软删（先到先得），按不存在收敛
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "发票抬头不存在");
        }
        log.info("[invoice-title][update] userId={} titleId={} 更新成功", userId, id);
    }

    /**
     * INV9 删除常用抬头（软删可恢复）。契约口径：默认抬头删除后<b>不自动递补</b>新默认
     * （用户下次申请时自行设置；不作任何清标记/递补动作）。
     *
     * @param userId 买家 user_id
     * @param id     抬头 id
     * @throws BizException       抬头不存在
     * @throws ForbiddenException 非本人抬头
     */
    public void deleteTitle(Long userId, Long id) {
        requireOwnedTitle(userId, id);
        invoiceTitleRepository.deleteByIdAndUserId(id, userId);
        log.info("[invoice-title][delete] userId={} titleId={} 软删成功", userId, id);
    }

    /**
     * 按抬头 id 加载并强校验本人归属（区分「不存在 1103」与「非本人 403」越权红线）。
     *
     * @throws BizException       抬头不存在（NO_RESOURCE_FOUND）
     * @throws ForbiddenException 非本人抬头
     */
    private InvoiceTitle requireOwnedTitle(Long userId, Long id) {
        InvoiceTitle title = invoiceTitleRepository.findById(id);
        if (title == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "发票抬头不存在");
        }
        if (!title.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "仅本人可操作该发票抬头");
        }
        return title;
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
