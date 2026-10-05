package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 促销活动类型枚举（F5 满减/满折促销引擎，V5.8）。
 *
 * <p>统一语言对齐：口径与 {@code promotion.type} 表注释、05-API-Specification §4.13.8b、
 * ADR-043 一致 —— 1=满减（FIXED，门槛满 X 减 Y）/ 2=满折（PERCENTAGE，门槛满 X 打 Y 折）；
 * 语义命名沿用券 {@code CouponTypeEnum} 的 FIXED/PERCENTAGE 先例（该枚举已 @Deprecated，新代码用本枚举）。
 *
 * <p>{@code contractName} 为活动中心对外契约字符串（05-API-Specification §4.10.3 M12，
 * V5.11 定稿）：1满减 → {@code FULL_REDUCE} / 2满折 → {@code LIMIT_DISCOUNT}；
 * {@code NEW_USER} 暂无 promotion 数据源（Phase 2 接新人礼活动），不属于本枚举。
 */
@Getter
public enum PromotionTypeEnum {
    FIXED(1, "满减", "FULL_REDUCE"),
    PERCENTAGE(2, "满折", "LIMIT_DISCOUNT");

    private final int code;
    private final String desc;
    /** 活动中心对外契约字符串（M12 ActivityVO.type，V5.11 定稿）。 */
    private final String contractName;

    PromotionTypeEnum(int code, String desc, String contractName) {
        this.code = code;
        this.desc = desc;
        this.contractName = contractName;
    }

    /**
     * 按编码解析枚举常量。
     *
     * @param code 类型编码（1/2）
     * @return 匹配的枚举常量；编码非法时返回 null（由调用方决定失败口径）
     */
    public static PromotionTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PromotionTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /**
     * 按对外契约字符串解析枚举常量（M12 {@code type} 查询参数口径）。
     *
     * @param contractName 契约字符串（{@code FULL_REDUCE} / {@code LIMIT_DISCOUNT}）
     * @return 匹配的枚举常量；字符串未知时返回 null（由调用方决定失败口径）
     */
    public static PromotionTypeEnum ofContractName(String contractName) {
        if (contractName == null) {
            return null;
        }
        for (PromotionTypeEnum type : values()) {
            if (type.contractName.equals(contractName)) {
                return type;
            }
        }
        return null;
    }
}
