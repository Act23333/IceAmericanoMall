package org.icedamericanomall.trade.infrastructure.persistence.finance;

import lombok.Data;

/**
 * 「还可核销额」查询结果（MyBatis 映射用；不承载业务行为）。
 *
 * <p>为什么不直接映射成 {@code SettlementRemaining} 记录：MyBatis 对 record 需要构造参数映射，
 * 这里用最薄的 PO 接一下，再在 RepositoryImpl 里转成领域记录 —— 与仓库既有的 PO/Domain 分离一致。
 */
@Data
public class SettlementRemainingPO {

    /** 结算单技术主键。 */
    private Long id;

    /** 可核销余额（分）= settlement_amount − withdrawn_amount。 */
    private Integer remaining;
}
