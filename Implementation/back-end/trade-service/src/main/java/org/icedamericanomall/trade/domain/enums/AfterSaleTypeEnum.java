package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

/**
 * 售后申请类型枚举 —— 对应 {@code after_sale.type}（口径与 {@code 04-Data-Model} 一致）。
 *
 * <pre>
 * 1-RETURN_REFUND 退货退款（需寄回商品，填 logisticsNumber）
 * 2-REFUND_ONLY   仅退款（不寄回，适用于未收到货/协商补偿）
 * </pre>
 *
 * <p>{@link #MIN_CODE}/{@link #MAX_CODE} 为「合法码连续区间」口径，供适配层 Bean Validation
 * 以编译期常量引用；{@link #of(Integer)} 是取值合法性的唯一事实来源。
 */
@Getter
public enum AfterSaleTypeEnum {

    RETURN_REFUND(1, "退货退款"),
    REFUND_ONLY(2, "仅退款");

    /** 合法码下界（与 {@link #RETURN_REFUND} 的 code 一致）。 */
    public static final int MIN_CODE = 1;

    /** 合法码上界（与 {@link #REFUND_ONLY} 的 code 一致）。 */
    public static final int MAX_CODE = 2;

    private final int code;
    private final String desc;

    AfterSaleTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按类型码解析枚举。
     *
     * @param code 类型码
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static AfterSaleTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AfterSaleTypeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
