package org.icedamericanomall.trade.adapter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 管理端「批量结算到期结算单」请求。
 *
 * <p>两个字段都是可选的：不带 = 全量扫（受单批上限约束）；带 {@code periodEnd} = 只补跑该账期。
 * <b>为什么要有 periodEnd</b>：运营补跑某一天时，若不按账期过滤，会把当前所有到期单一起结算，
 * 事后无法区分「补跑的那批」与「正常到期的那批」。
 */
@Data
public class SettlementSweepReq {

    /** 单批上限；不传由服务端默认（200）。上限做校验，避免一次把全表扫了。 */
    @Min(1)
    @Max(1000)
    private Integer limit;

    /** 只结算该账期的单（{@code period_end}）；null = 不按账期过滤。 */
    private LocalDate periodEnd;
}
