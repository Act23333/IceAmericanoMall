package org.icedamericanomall.trade.adapter.assembler;


import org.icedamericanomall.trade.adapter.response.AfterSaleVO;
import org.icedamericanomall.trade.adapter.response.SettlementVO;
import org.icedamericanomall.trade.adapter.response.WithdrawalVO;
import org.icedamericanomall.trade.domain.model.aftersale.AfterSale;
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 交易域 售后/结算/提现 聚合 → VO 装配器。
 * （入驻申请平行流程已退役：入驻归商家域 merchant-service，ADR-016 收尾）
 *
 * <p><b>提现收款账号装配口径（V5.4）</b>：{@link #toVO(Withdrawal)} 为<b>管理端</b>视图
 * （脱敏号恒填、明文保留，打款需完整账号）；{@link #toSellerVO(Withdrawal)} 为<b>商家端</b>视图
 * （脱敏号恒填、明文置 null）。两个端点共用 {@code WithdrawalVO}，仅装配不同。
 *
 * <p><b>账号掩码来源（V5.5 收口）</b>：脱敏号统一走 ia-common
 * {@code DesensitizeUtil#maskAccount}（保留末 4 位、≤ 4 位全遮）。
 */
@Mapper(componentModel = "spring")
public interface TradeConverter {

    AfterSaleVO toVO(AfterSale afterSale);

    SettlementVO toVO(Settlement settlement);

    /**
     * 提现单 → 管理端视图：脱敏号由原账号派生，明文保留（{@code /api/admin/finance/**} 已鉴权）。
     *
     * @param withdrawal 提现聚合
     * @return 含明文账号 + 脱敏号的 VO
     */
    @Mapping(target = "bankAccountMasked",
            expression = "java(org.icedamericanomall.common.utils.crypto.DesensitizeUtil.maskAccount(withdrawal.getBankAccount()))")
    WithdrawalVO toVO(Withdrawal withdrawal);

    /**
     * 提现单 → 商家端视图（{@code /api/seller/finance/withdrawal[/page]}）：
     * 仅回脱敏号，明文账号置 {@code null}，防止商家端页面/接口抓包泄露完整收款账号。
     *
     * @param withdrawal 提现聚合
     * @return 明文为 null、脱敏号已填的 VO
     */
    default WithdrawalVO toSellerVO(Withdrawal withdrawal) {
        WithdrawalVO vo = toVO(withdrawal);
        if (vo != null) {
            vo.setBankAccount(null);
        }
        return vo;
    }
}
