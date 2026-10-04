package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 秒杀活动状态枚举。
 *
 * <p>V5.7 统一语言对齐：代码口径与 {@code flash_sale.status} 表注释、
 * 05-API-Specification §4.13.14 一致 —— 1=未开始、2=进行中、3=已结束（活动状态由时间窗推导，
 * 见 {@code FlashSale.deriveStatus}）；0/4 为历史遗留值，仅存量兼容。
 */
@Getter
public enum FlashSaleStatusEnum {
    DRAFT(0, "草稿"),
    PENDING(1, "未开始"),
    ACTIVE(2, "进行中"),
    ENDED(3, "已结束"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String desc;

    FlashSaleStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
