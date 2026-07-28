package org.icedamericanomall.enums;

import lombok.Getter;

/** V4.0: 热销原因枚举（京东/淘宝标准：折扣/新品/爆款/清仓） */
@Getter
public enum HotReasonEnum {
    DISCOUNT("discount", "限时优惠"),
    NEW_ARRIVAL("new_arrival", "新品上市"),
    BEST_SELLER("best_seller", "热卖爆款"),
    CLEARANCE("clearance", "清仓特价");

    private final String code;
    private final String desc;

    HotReasonEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
