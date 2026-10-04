package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提现申请视图对象（商家提现记录 / 管理后台审核列表共用）。
 *
 * <p><b>收款账号口径（V5.4 决策：明文不下商家端）</b>：
 * <ul>
 *   <li><b>商家端</b>（{@code /api/seller/finance/withdrawal}、{@code /api/seller/finance/withdrawal/page}）——
 *       只返回脱敏号 {@link #bankAccountMasked}（如 {@code ****1234}），{@link #bankAccount} 置 {@code null}；</li>
 *   <li><b>管理端</b>（{@code /api/admin/finance/withdrawal/page}，已有权限保护）—— 两个字段都返回，
 *       打款需完整账号核对，故保留明文；</li>
 * </ul>
 * 装配差异由 {@code TradeConverter#toVO}（管理端）/ {@code TradeConverter#toSellerVO}（商家端）承担，
 * 两个端点的响应类共用本 VO，避免平行 DTO 重复。
 *
 * <p>{@code id} 必留（审核端点 {@code PUT /api/admin/finance/withdrawal/{id}/review} 以它定位）；
 * {@code adminRemark} 是驳回原因，商家端必须可见；{@code createTime} 为列表展示所需；
 * 审计字段 {@code updateTime} 从未暴露。
 */
@Data
public class WithdrawalVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String withdrawalNo;
    private Long sellerId;
    private Integer amount;
    private Integer status;

    /**
     * 收款账号明文 —— <b>仅管理端返回</b>；商家端恒为 {@code null}。
     */
    private String bankAccount;

    /**
     * 收款账号脱敏号（保留末 4 位，如 {@code ****1234}）—— 所有端均返回。
     */
    private String bankAccountMasked;

    private String bankName;
    private String adminRemark;
    private LocalDateTime createTime;
}
