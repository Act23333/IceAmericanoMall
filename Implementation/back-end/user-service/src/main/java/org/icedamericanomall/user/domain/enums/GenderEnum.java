package org.icedamericanomall.user.domain.enums;

import lombok.Getter;

/**
 * 性别（闭集取值域）。
 *
 * <p>持久化口径为 Integer code（user 表 {@code gender TINYINT}），
 * 报文口径同为 Integer（{@code /api/user/info}）；领域内以本枚举表达，
 * PO 字段保持原始类型，枚举 ⇄ code 映射由 {@code UserConverter} 显式承担。
 */
@Getter
public enum GenderEnum {

    /** 未知 */
    UNKNOWN(0),

    /** 男 */
    MALE(1),

    /** 女 */
    FEMALE(2);

    private final int code;

    GenderEnum(int code) {
        this.code = code;
    }

    /**
     * 按持久化/报文 code 反查。
     *
     * @param code 持久化/报文 code（可为 null）
     * @return 对应枚举；code 为 null 或无法识别时返回 {@code null}（错误语义由调用方决定）
     */
    public static GenderEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (GenderEnum gender : values()) {
            if (gender.code == code) {
                return gender;
            }
        }
        return null;
    }
}
