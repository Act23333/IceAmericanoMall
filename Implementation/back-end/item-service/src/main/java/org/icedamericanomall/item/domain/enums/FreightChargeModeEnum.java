package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 运费计费方式枚举（{@code freight_template.charge_mode}，F2 运费模型）。
 *
 * <pre>
 * 1-FIXED 固定运费（Phase 1 唯一值；为 Phase 2 按件 / 按重计费预留）
 * </pre>
 */
@Getter
public enum FreightChargeModeEnum {

    FIXED(1, "固定运费");

    private final int code;
    private final String desc;

    FreightChargeModeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按计费方式码解析枚举。
     *
     * @param code 计费方式码
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static FreightChargeModeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FreightChargeModeEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
