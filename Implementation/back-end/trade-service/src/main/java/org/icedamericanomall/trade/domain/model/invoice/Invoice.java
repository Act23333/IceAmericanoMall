package org.icedamericanomall.trade.domain.model.invoice;

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.icedamericanomall.trade.domain.enums.InvoiceStatusEnum;
import org.icedamericanomall.trade.domain.enums.InvoiceTitleTypeEnum;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 发票单聚合（纯 POJO，零框架依赖）—— {@code invoice} 表（F14 发票，PRD §十五）。
 *
 * <pre>
 * 状态机（{@link InvoiceStatusEnum}，全部迁移由仓储「条件更新」承载，本类只做前置守卫）:
 *   1-待开具 → 2-已开具（管理端模拟开具） / 4-已作废（撤回/全额退款守卫/管理端作废）
 *   2-已开具 → 3-已冲红（全额退款自动 / 管理端冲红 / 换开前置）
 *   3/4 为终态（重复触发 = 幂等空操作）
 *
 * 不变量:
 *   1. 一单一有效票 —— activeFlag=1 占 {@code uk_order_active(order_no, active_flag)} 槽位，
 *      迁入终态时由仓储置 active_flag=NULL 让出槽位（MySQL 唯一索引多 NULL 特性）
 *   2. 金额 = 申请时 orders.payAmount 快照（决策 D3，含运费扣优惠），落库后不再随订单变化
 *   3. 抬头为申请时快照（个人=姓名；企业=企业名称+税号），抬头表变更不影响已申请发票
 *   4. 企业抬头税号必填；税号/邮箱按 PII 三字段模型持有（{@link EncryptedIdentifier}）
 * </pre>
 */
@Data
public class Invoice {

    /** 抬头快照最大长度（对齐 DDL {@code title VARCHAR(100)}）。 */
    public static final int TITLE_MAX_LENGTH = 100;

    private Long id;

    /** 发票申请号（业务唯一键，UUID，{@code uk_invoice_no}） */
    private String invoiceNo;

    /** 关联订单号（orders.order_no，无外键） */
    private String orderNo;

    /** 有效票标记：1=有效（待开具/已开具）；NULL=终态（已冲红/已作废） */
    private Integer activeFlag;

    /** 买家 user_id */
    private Long userId;

    /** 开票主体商家 user_id（= orders.seller_id，决策 D1） */
    private Long sellerId;

    /** 抬头类型（1=个人 2=企业） */
    private Integer type;

    /** 抬头快照（个人=姓名；企业=企业名称） */
    private String title;

    /** 企业税号（PII 三字段：cipher + hash + mask；个人抬头为 null） */
    private EncryptedIdentifier taxNo;

    /** 交付邮箱（PII 三字段；未填为 null） */
    private EncryptedIdentifier email;

    /** 开票金额（分）= 申请时 payAmount 快照 */
    private Integer amount;

    /** 状态（1 待开具 2 已开具 3 已冲红 4 已作废） */
    private Integer status;

    /** 开具时间 */
    private LocalDateTime issueTime;

    /** 模拟票号（Phase 2 语义替换为服务商票号） */
    private String mockInvoiceNo;

    /** 样票 MinIO objectKey（决策 O5） */
    private String pdfKey;

    /** 冲红关联：换开/重开生成的新票指向被冲红/终态原票 id */
    private Long redForId;

    /** 冲红/作废原因（自动=系统原因码；人工必填） */
    private String redReason;

    /** 部分退款待人工处理标记（0/1，决策 D4/O6：不改状态只置标记） */
    private Integer partialRefundFlag;

    /** 最近催开时间（24h 限频判定，O10） */
    private LocalDateTime urgeTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建发票申请（待开具，未落库状态，{@code id} 由仓储落库时补齐）。
     *
     * <p>自封装守卫：抬头类型必须合法、抬头必填且 ≤ 上限、企业抬头<b>税号必填</b>、
     * 开票金额必须为正（分）。税号/邮箱在创建时即加密为三字段（cipher + hash + mask），
     * 明文不落聚合字段（对齐 user 聚合先例）。
     *
     * @param orderNo  订单号
     * @param userId   买家 user_id
     * @param sellerId 开票主体商家 user_id
     * @param type     抬头类型（1=个人 2=企业）
     * @param title    抬头快照
     * @param taxNo    企业税号明文（个人抬头传 null）
     * @param email    交付邮箱明文（可空）
     * @param amount   开票金额（分，= payAmount 快照）
     * @param redForId 冲红关联原票 id（换开/重开时关联，首次申请为 null）
     * @return 新发票聚合（状态=待开具，activeFlag=1）
     * @throws BizException 类型非法 / 抬头缺失 / 企业缺税号 / 金额非法（均为 PARAM_ERROR）
     */
    public static Invoice create(String orderNo, Long userId, Long sellerId, Integer type,
                                 String title, String taxNo, String email, int amount, Long redForId) {
        InvoiceTitleTypeEnum typeEnum = InvoiceTitleTypeEnum.of(type);
        if (typeEnum == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发票抬头类型非法（1=个人 2=企业）");
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发票抬头不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发票抬头超长（≤" + TITLE_MAX_LENGTH + "字）");
        }
        boolean company = typeEnum == InvoiceTitleTypeEnum.COMPANY;
        if (company && (taxNo == null || taxNo.isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "企业抬头必须填写税号");
        }
        if (amount <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "开票金额非法");
        }
        Invoice invoice = new Invoice();
        invoice.invoiceNo = UUID.randomUUID().toString();
        invoice.orderNo = orderNo;
        invoice.userId = userId;
        invoice.sellerId = sellerId;
        invoice.type = type;
        invoice.title = title;
        invoice.taxNo = company ? encryptedTaxNo(taxNo) : null;
        invoice.email = (email == null || email.isBlank()) ? null : EncryptedIdentifier.fromPlain(email);
        invoice.amount = amount;
        invoice.redForId = redForId;
        invoice.status = InvoiceStatusEnum.PENDING_ISSUE.getCode();
        invoice.activeFlag = 1;
        invoice.partialRefundFlag = 0;
        return invoice;
    }

    /**
     * 企业税号三字段（O4 从严）—— cipher/hash 复用统一三字段模型；掩码显式走账号类规则：
     * {@code DesensitizeUtil.mask} 通用入口只识别手机号/邮箱形态，税号会原样返回明文，
     * 必须改用 {@code maskAccount} 尾 4 口径（掩码规则唯一出口，ADR-034）。
     *
     * <p>包内共享：同包 {@link InvoiceTitle}（常用抬头）创建/更新税号时复用本方法，
     * 保证税号三字段组合口径（cipher + hash + maskAccount 掩码）单一出处。
     */
    static EncryptedIdentifier encryptedTaxNo(String taxNo) {
        EncryptedIdentifier identifier = EncryptedIdentifier.fromPlain(taxNo);
        return EncryptedIdentifier.fromStorage(identifier.getCipher(), identifier.getHash(),
                DesensitizeUtil.maskAccount(taxNo));
    }

    // ==================== 守卫（纯函数） ====================

    /** 是否待开具（占有效槽位、可开具/可撤回/可作废/可催开）。 */
    public boolean isPending() {
        return status != null && InvoiceStatusEnum.PENDING_ISSUE.getCode() == status;
    }

    /** 是否已开具（可冲红/可下载样票；换开的唯一前置态）。 */
    public boolean isIssued() {
        return status != null && InvoiceStatusEnum.ISSUED.getCode() == status;
    }

    /**
     * 管理端开具前置守卫 —— 仅「待开具」可开具。
     *
     * @throws BizException 状态非待开具（PARAM_ERROR 带文案）
     */
    public void assertIssueable() {
        if (!isPending()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅待开具发票可开具，当前状态："
                    + descOf(status));
        }
    }

    /**
     * 买家撤回前置守卫 —— 仅「待开具」可撤回（已开具走换开/冲红，终态不可撤回）。
     *
     * @throws BizException 状态非待开具（PARAM_ERROR 带文案）
     */
    public void assertBuyerCancellable() {
        if (!isPending()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前发票状态不支持撤回（"
                    + descOf(status) + "）");
        }
    }

    /**
     * 管理端作废前置守卫 —— 仅「待开具」可作废；<b>已开具不可作废，只能冲红</b>
     * （防"作废已开票"的合规漏洞，PRD 守卫清单 #1）。
     *
     * @throws BizException 已开具（合规守卫）或终态（PARAM_ERROR 带文案）
     */
    public void assertVoidable() {
        if (isIssued()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已开具发票不可作废，请走冲红");
        }
        assertBuyerCancellable();
    }

    /**
     * 冲红前置守卫 —— 仅「已开具」可冲红（待开具走作废，已作废为终态；
     * 已冲红的重复触发 = 幂等空操作，由应用层在调用本守卫前按幂等收敛）。
     *
     * @throws BizException 状态非已开具（PARAM_ERROR 带文案）
     */
    public void assertRedFlushable() {
        if (!isIssued()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅已开具发票可冲红，当前状态："
                    + descOf(status));
        }
    }

    /** 状态描述（非法状态码降级为原值文案，防御式兜底）。 */
    private static String descOf(Integer status) {
        InvoiceStatusEnum statusEnum = InvoiceStatusEnum.of(status);
        return statusEnum == null ? String.valueOf(status) : statusEnum.getDesc();
    }
}
