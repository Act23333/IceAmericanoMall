package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 促销活动状态枚举（F5 促销引擎，V5.8）。
 *
 * <p>统一语言对齐：口径与 {@code promotion.status} 表注释、05-API-Specification §4.13.8b、
 * ADR-043 一致 —— 1=未开始、2=进行中、3=已结束；状态一律由 {@code startTime}/{@code endTime}
 * 时间窗实时推导（{@code Promotion.deriveStatus}），落库 {@code status} 仅是推导结果在写入时刻的快照，
 * 管理端守卫与 I44 计价查询均不信任快照（复用 AD40-42 秒杀先例）。
 */
@Getter
public enum PromotionStatusEnum {
    PENDING(1, "未开始"),
    ACTIVE(2, "进行中"),
    ENDED(3, "已结束");

    private final int code;
    private final String desc;

    PromotionStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
