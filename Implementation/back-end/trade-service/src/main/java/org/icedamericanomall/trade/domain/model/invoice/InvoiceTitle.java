package org.icedamericanomall.trade.domain.model.invoice;

import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.trade.domain.enums.InvoiceTitleTypeEnum;

import java.time.LocalDateTime;

/**
 * 常用发票抬头聚合（纯 POJO）—— {@code invoice_title} 表（PRD §15.4.2，决策 O2 随批简版）。
 *
 * <p>定位：仅服务申请体验（INV1 {@code titleId} 引用），发票单持有的是<b>申请时快照</b>，
 * 本表增删改不影响已申请发票。默认抬头唯一性由 {@code uk_user_default(user_id, is_default)}
 * 多 NULL 特性兜底（一用户至多一条默认），换设默认前须先清旧默认（仓储
 * {@code clearDefaultFlag} 语义，应用层编排）。软删（{@code deleted} {@code @TableLogic}，可恢复）。
 */
@Data
public class InvoiceTitle {

    /** 默认抬头标记值（is_default=1；NULL=非默认，uk_user_default 多 NULL 语义）。 */
    public static final int DEFAULT_FLAG = 1;

    private Long id;

    /** 所属用户 user_id */
    private Long userId;

    /** 抬头类型（1=个人 2=企业） */
    private Integer type;

    /** 抬头（个人=姓名；企业=企业名称） */
    private String title;

    /** 企业税号（PII 三字段；个人抬头为 null） */
    private EncryptedIdentifier taxNo;

    /** 是否默认抬头（1=默认；null=非默认，uk_user_default 多 NULL 语义） */
    private Integer isDefault;

    /** 软删标记（@TableLogic，持久化层承载） */
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 创建常用抬头（未落库状态，{@code id} 由仓储落库时补齐）。
     *
     * <p>自封装守卫与要素口径见 {@link #applyFullUpdate}；个人抬头忽略报文税号
     * （PII 从严：非企业不落任何税号字段）。
     *
     * @param userId      所属用户 user_id
     * @param type        抬头类型（1=个人 2=企业）
     * @param title       抬头（个人=姓名，企业=企业名称）
     * @param taxNo       企业税号明文（个人抬头忽略，可传 null）
     * @param defaultFlag 是否设为默认抬头
     * @return 新抬头聚合
     * @throws BizException 类型非法 / 抬头缺失或超长 / 企业缺税号（PARAM_ERROR）
     */
    public static InvoiceTitle create(Long userId, Integer type, String title,
                                      String taxNo, boolean defaultFlag) {
        InvoiceTitle invoiceTitle = new InvoiceTitle();
        invoiceTitle.setUserId(userId);
        invoiceTitle.applyFullUpdate(type, title, taxNo, defaultFlag);
        return invoiceTitle;
    }

    // ==================== 行为 ====================

    /**
     * 全量更新抬头要素（INV8 语义：类型/抬头/税号/默认标记整体替换）。
     *
     * <p>自封装守卫：类型必须合法、抬头必填且 ≤ 上限、企业抬头<b>税号必填</b>；
     * 税号在企业抬头时加密为三字段（cipher + hash + maskAccount 掩码，复用
     * {@link Invoice#encryptedTaxNo} 单一口径），个人抬头一律置 null（清列语义）。
     *
     * @param type        抬头类型（1=个人 2=企业）
     * @param title       抬头
     * @param taxNo       企业税号明文（个人抬头忽略）
     * @param defaultFlag 是否设为默认抬头
     * @throws BizException 类型非法 / 抬头缺失或超长 / 企业缺税号（PARAM_ERROR）
     */
    public void applyFullUpdate(Integer type, String title, String taxNo, boolean defaultFlag) {
        InvoiceTitleTypeEnum typeEnum = InvoiceTitleTypeEnum.of(type);
        if (typeEnum == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发票抬头类型非法（1=个人 2=企业）");
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "发票抬头不能为空");
        }
        if (title.length() > Invoice.TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "发票抬头超长（≤" + Invoice.TITLE_MAX_LENGTH + "字）");
        }
        boolean company = typeEnum == InvoiceTitleTypeEnum.COMPANY;
        if (company && (taxNo == null || taxNo.isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "企业抬头必须填写税号");
        }
        this.type = type;
        this.title = title;
        this.taxNo = company ? Invoice.encryptedTaxNo(taxNo) : null;
        this.isDefault = defaultFlag ? DEFAULT_FLAG : null;
    }

    /** 是否默认抬头（is_default=1；NULL 视为非默认）。 */
    public boolean isDefaultTitle() {
        return isDefault != null && isDefault == DEFAULT_FLAG;
    }
}
