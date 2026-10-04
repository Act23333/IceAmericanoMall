package org.icedamericanomall.logistics.domain.enums;


import lombok.Getter;

/**
 * 物流状态枚举 — 物流单生命周期。
 *
 * <pre>
 * State flow:
 *   PENDING(1) → SHIPPED(2) → DELIVERED(3)
 *                            ↘ RETURNED(4)
 * </pre>
 */
@Getter
public enum LogisticsStatusEnum {
    PENDING(1, "待揽收"),
    SHIPPED(2, "运输中"),
    DELIVERED(3, "已签收"),
    RETURNED(4, "已退回");


    private final int code;
    private final String desc;

    LogisticsStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据 code 获取枚举，找不到返回 null。
     */
    public static LogisticsStatusEnum of(Integer code) {
        if (code == null) return null;
        for (LogisticsStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }

    /**
     * 是否允许由当前状态单调迁移到目标状态（不允许回退到更早阶段）。
     *
     * <pre>
     *   PENDING  → SHIPPED
     *   SHIPPED  → DELIVERED | RETURNED
     *   DELIVERED↔RETURNED
     * </pre>
     *
     * @param target 目标状态
     * @return true=合法迁移（同状态的重放不算迁移，返回 false，由调用方幂等处理）
     */
    public boolean canTransitTo(LogisticsStatusEnum target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == SHIPPED;
            case SHIPPED -> target == DELIVERED || target == RETURNED;
            case DELIVERED -> target == RETURNED;
            case RETURNED -> target == DELIVERED;
        };
    }
}
