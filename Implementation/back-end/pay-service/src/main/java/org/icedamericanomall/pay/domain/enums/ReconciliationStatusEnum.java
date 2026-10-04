package org.icedamericanomall.pay.domain.enums;

import lombok.Getter;

/**
 * 对账批次状态枚举 —— 对应 {@code reconciliation_batch.status}（F4 渠道对账）。
 */
@Getter
public enum ReconciliationStatusEnum {

    PROCESSING(0, "处理中"),
    COMPLETED(1, "完成"),
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    ReconciliationStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
