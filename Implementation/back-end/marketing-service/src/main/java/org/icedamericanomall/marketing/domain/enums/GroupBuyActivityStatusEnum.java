package org.icedamericanomall.marketing.domain.enums;

import lombok.Getter;

/**
 * 拼团活动状态枚举（F13 拼团，V5.14，ADR-049）。
 *
 * <p>统一语言对齐：口径与 {@code group_buy_activity.status} 表注释、05-API-Specification §4.13.8d、
 * ADR-049 一致 —— 1=未开始、2=进行中、3=已结束；状态一律由 {@code startTime}/{@code endTime}
 * 时间窗实时推导（{@code GroupBuyActivity.deriveStatus}），落库 {@code status} 仅是推导结果在
 * 写入时刻的快照，管理端守卫与 I52 校验均不信任快照（复用 AD40-42/AD48-50 先例）。
 */
@Getter
public enum GroupBuyActivityStatusEnum {
    PENDING(1, "未开始"),
    ACTIVE(2, "进行中"),
    ENDED(3, "已结束");

    private final int code;
    private final String desc;

    GroupBuyActivityStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
