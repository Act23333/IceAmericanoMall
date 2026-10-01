package org.icedamericanomall.auth.domain.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证凭证状态。
 *
 * <p>领域枚举，<b>不依赖任何持久化框架</b>；与库表 {@code status} 整型的映射
 * 由基础设施层的 Converter 负责。
 */
@Getter
public enum UserAuthStatusEnum {

    /** 已解绑（保留历史，不做物理删除） */
    UNBOUND(0, "已解绑"),

    /** 有效 */
    ACTIVE(1, "有效"),

    /** 已锁定 */
    LOCKED(2, "已锁定");

    private final int code;

    private final String desc;

    UserAuthStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<Integer, UserAuthStatusEnum> CODE_MAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(UserAuthStatusEnum::getCode, e -> e));

    /**
     * 根据 code 反查枚举，未知 code 返回 null（而非抛异常，避免脏数据导致查询崩溃）
     */
    public static UserAuthStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }

    /** 是否处于可登录状态 */
    public static boolean isActive(UserAuthStatusEnum status) {
        return status == ACTIVE;
    }
}
