package org.icedamericanomall.item.domain.enums;

import lombok.Getter;

/**
 * 商品状态（{@code spu.status}）—— 领域枚举，零框架依赖。
 *
 * <p>持久化字段保持原始类型（{@link Integer}），枚举 ⇄ code 的换算由
 * {@code org.icedamericanomall.item.infrastructure.persistence.spu.SpuConverter} 显式承担，
 * 避免 ORM 注解污染领域枚举（V5.7 四层化：原 {@code @EnumValue} 已移除，
 * 数据库枚举处理器统一由 {@code MybatisEnumTypeHandler} 兜底，实体无枚举类型字段）。
 */
@Getter
public enum SpuStatusEnum {

    ON_SHELF(1, "上架"),
    OFF_SHELF(2, "下架"),
    DELETED(3, "删除");

    private final int code;
    private final String desc;

    SpuStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按状态码解析枚举。
     *
     * @param code 状态码（可空）
     * @return 匹配的枚举；不存在（含 null）返回 {@code null}
     */
    public static SpuStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (SpuStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
