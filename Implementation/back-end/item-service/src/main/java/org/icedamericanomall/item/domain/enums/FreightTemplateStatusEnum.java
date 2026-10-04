package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 运费模板状态枚举（{@code freight_template.status}，F2 运费模型）。
 *
 * <pre>
 * 1-ENABLED  启用（参与下单计价）
 * 0-DISABLED 停用（不参与计价；商品绑定停用模板时回落商家默认模板）
 * </pre>
 */
@Getter
public enum FreightTemplateStatusEnum {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    FreightTemplateStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static FreightTemplateStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FreightTemplateStatusEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
