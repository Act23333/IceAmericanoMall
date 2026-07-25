package org.icedAmericanoMall.constants;

import lombok.Getter;

/** V3.7: 秒杀活动状态枚举（大厂标准：京东秒杀状态机） */
@Getter
public enum FlashSaleStatusEnum {
    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
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
