package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * 对账差异结果枚举 —— 对应 {@code reconciliation_detail.result}（F4 渠道对账，四态）。
 *
 * <p><b>Phase 1 口径</b>：四态仅<b>落库记录供管理端查看</b>，不自动调账（人工处理）。
 */
@Getter
public enum ReconciliationResultEnum {

    /** 本地与渠道一致（支付单号与金额均匹配）。 */
    MATCHED(1, "匹配"),
    /** 渠道有、本地无（可能为本地漏单/渠道串单）。 */
    CHANNEL_ONLY(2, "渠道有本地无"),
    /** 本地有、渠道无（可能为渠道 T+1 未出账/本地虚记）。 */
    LOCAL_ONLY(3, "本地有渠道无"),
    /** 支付单号一致但金额不一致。 */
    AMOUNT_MISMATCH(4, "金额不一致");

    private final int code;
    private final String desc;

    ReconciliationResultEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
