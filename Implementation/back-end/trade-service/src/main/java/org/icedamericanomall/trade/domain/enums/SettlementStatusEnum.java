package org.icedamericanomall.trade.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 结算单状态。
 *
 * <p><b>语义（写死，别按字面猜）</b>：
 * <ul>
 *   <li>{@code PENDING(1)} 待结算 —— 账期未走完/尚未结算；</li>
 *   <li>{@code SETTLED(2)} 已结算 —— <b>= 可提现</b>，<b>不代表「未打款」</b>（它不承载打款事实）；</li>
 *   <li>{@code PAID_OUT(3)} 已打款 —— <b>历史语义</b>，T-WITHDRAW 第一步起<b>不再写入</b>。
 *       「已打款」的事实源是 {@code withdrawal.status = PAID_OUT}（聚合看
 *       {@code seller_fund_account.paid_total}），**不要**从结算单反推。</li>
 * </ul>
 * 保留 {@code PAID_OUT} 是因为历史数据与列注释仍引用它；新增代码不应再产生该状态。
 */
@Getter
public enum SettlementStatusEnum {
    PENDING(1, "待结算"),
    SETTLED(2, "已结算"),
    PAID_OUT(3, "已打款");

    @EnumValue
    private final int code;
    private final String desc;

    SettlementStatusEnum(int code, String desc) { this.code = code; this.desc = desc; }
}
