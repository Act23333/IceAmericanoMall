package org.icedamericanomall.user.domain.enums;

import lombok.Getter;

/**
 * 会员等级（闭集取值域：普通/白银/黄金/铂金）。
 *
 * <p>持久化口径为 String code（user 表 {@code member_level VARCHAR}），
 * 报文口径同为 String；领域内以本枚举表达，归一化规则 = trim + 大写。
 * 未来等级规则营销配置化（成长值/权益规则）时，仅替换持久化映射，领域行为不动。
 */
@Getter
public enum MemberLevelEnum {

    /** 普通会员（注册即得） */
    NORMAL("NORMAL"),

    /** 白银会员 */
    SILVER("SILVER"),

    /** 黄金会员 */
    GOLD("GOLD"),

    /** 铂金会员 */
    PLATINUM("PLATINUM");

    private final String code;

    MemberLevelEnum(String code) {
        this.code = code;
    }

    /**
     * 按 code 反查（trim + 大写归一化）。
     *
     * @param code 持久化/报文 code（可为 null/空白）
     * @return 对应枚举；code 为 null/空白或无法识别时返回 {@code null}（错误语义由调用方决定）
     */
    public static MemberLevelEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (MemberLevelEnum level : values()) {
            if (level.code.equals(normalized)) {
                return level;
            }
        }
        return null;
    }
}
