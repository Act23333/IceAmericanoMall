package org.icedamericanomall.user.domain.enums;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

/**
 * 用户状态（领域枚举，纯 POJO —— 无 MyBatis 注解）。
 *
 * <p>取值与 {@code user.status} 列一致：{@code 1}=正常，{@code 0}=禁用。
 * 持久化层用 {@code Integer} 映射该列，DO ↔ 领域模型转换在
 * {@code infrastructure/persistence/user/UserConverter} 完成，
 * 避免把 ORM 的 {@code @EnumValue} 依赖带进领域层。
 */
public enum UserStatusEnum {

    FROZEN(0, "禁止使用"),
    NORMAL(1, "已激活"),
    ;

    private final Integer code;
    private final String description;

    UserStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 状态码（与 {@code user.status} 列取值一致）。 */
    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 从状态码还原；非法值抛业务异常（管理后台入参校验）。 */
    public static UserStatusEnum of(int value) {
        if (value == 0) {
            return FROZEN;
        }
        if (value == 1) {
            return NORMAL;
        }
        throw new BadRequestException(ErrorCode.USER_STATUS_ABNORMAL, "账户状态错误");
    }

    /** 从状态码还原（null 安全，供持久化转换使用）。 */
    public static UserStatusEnum fromCode(Integer value) {
        return value == null ? null : of(value);
    }
}
