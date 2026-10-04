package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 热销原因（{@code sku.hot_reason}）—— 领域枚举，零框架依赖。
 *
 * <p>发布商品时按白名单归一：非法编码不阻断发布，只丢弃脏值（返回 {@code null}）。
 */
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

    /**
     * 按编码归一热销原因，非法编码返回 null（不阻断商品发布，只丢弃脏值）。
     *
     * @param code 前端传入的热销原因编码
     * @return 命中的枚举编码，未命中或空值时为 null
     */
    public static String normalize(String code) {
        HotReasonEnum reason = fromCode(code);
        return reason == null ? null : reason.code;
    }

    /** 按编码查找枚举。 */
    public static HotReasonEnum fromCode(String code) {
        for (HotReasonEnum reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        return null;
    }
}
