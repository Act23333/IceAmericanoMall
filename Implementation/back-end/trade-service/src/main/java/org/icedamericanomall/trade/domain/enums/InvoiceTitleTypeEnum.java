package org.icedamericanomall.trade.domain.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 发票抬头类型（PRD §15.4.2，F14 发票）。
 *
 * <p>PERSONAL = 个人抬头（抬头 = 姓名）；COMPANY = 企业抬头（抬头 = 企业名称，<b>税号必填</b>）。
 * 契约口径：报文与持久化均用 Integer code（1/2）。
 */
@Getter
public enum InvoiceTitleTypeEnum {

    /** 个人抬头（税号/邮箱均可空） */
    PERSONAL(1, "个人"),

    /** 企业抬头（税号必填，PII 三字段落库） */
    COMPANY(2, "企业");

    private final int code;
    private final String desc;

    InvoiceTitleTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按类型码解析枚举。
     *
     * @param code 类型码
     * @return 命中的枚举；未命中返回 {@code null}
     */
    public static InvoiceTitleTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst()
                .orElse(null);
    }
}
