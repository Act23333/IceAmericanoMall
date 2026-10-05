package org.icedamericanomall.marketing.domain.model.promotion;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.PromotionStatusEnum;
import org.icedamericanomall.marketing.domain.enums.PromotionTypeEnum;

import java.time.LocalDateTime;

/**
 * 店铺满减/满折促销活动聚合（纯 POJO）—— {@code promotion} 表（F5 促销引擎，V5.8，ADR-043）。
 *
 * <p>店铺级活动（Phase 1：{@code sellerId} 必填，跨店下单已禁，一单仅触达单商家）。
 * 计价口径：{@code threshold}（门槛，分）+ {@code discountValue}（满减=减免分；
 * 满折=万分比 8500=8.5折）+ {@code maxDiscount}（满折封顶分，0=不封顶）；金额一律整数分。
 *
 * <p>{@code version} 为乐观锁列：编辑时随聚合携带，冲突由 MyBatis-Plus 拦截器拦截。
 *
 * <p><b>状态口径</b>：活动状态由 {@code startTime}/{@code endTime} 时间窗实时推导
 * （1未开始/2进行中/3已结束，{@link #deriveStatus}），落库 {@code status} 仅为写入时刻快照；
 * 管理端守卫（已结束禁编 / 进行中禁删）与 I44 计价查询均不信任快照（复用 AD40-42 秒杀先例）。
 */
@Data
@Builder
public class Promotion {

    /** 满折折扣率上限（万分比）：8500=8.5折，10000=10折（即原价，契约允许的边界值）。 */
    private static final int MAX_PERCENTAGE_DISCOUNT_VALUE = 10000;

    private Long id;
    private String name;
    private Long sellerId;
    private Integer type;
    private Integer threshold;
    private Integer discountValue;
    private Integer maxDiscount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;

    /**
     * 按时间窗推导活动状态（V5.8 契约口径）：未开始=1 / 进行中=2 / 已结束=3。
     *
     * @param now 判定时刻
     * @return 状态码，见 {@link PromotionStatusEnum}
     */
    public int deriveStatus(LocalDateTime now) {
        if (now.isBefore(this.startTime)) {
            return PromotionStatusEnum.PENDING.getCode();
        }
        if (now.isAfter(this.endTime)) {
            return PromotionStatusEnum.ENDED.getCode();
        }
        return PromotionStatusEnum.ACTIVE.getCode();
    }

    /**
     * 校验时间窗有序（endTime 严格晚于 startTime）—— 聚合不变式（AD48/AD49 字段口径）。
     *
     * @throws BizException 时间窗非法（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public void ensureTimeWindowOrdered() {
        if (this.startTime == null || this.endTime == null || !this.endTime.isAfter(this.startTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验优惠规则合法 —— 聚合不变式（跨字段，JSR-303 无法表达）：
     * 类型必须为 1满减/2满折；满折 {@code discountValue} 不得越过万分比上限
     * （8500=8.5折，10000 为契约允许边界，&gt;10000 属字段非法 → 1101）。
     *
     * @throws BizException 类型非法 / 满折折扣率越界（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public void ensureDiscountRuleLegal() {
        PromotionTypeEnum typeEnum = PromotionTypeEnum.of(this.type);
        if (typeEnum == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "促销类型非法: " + this.type);
        }
        if (typeEnum == PromotionTypeEnum.PERCENTAGE
                && (this.discountValue == null || this.discountValue > MAX_PERCENTAGE_DISCOUNT_VALUE)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "满折折扣率必须为不超过 " + MAX_PERCENTAGE_DISCOUNT_VALUE + " 的万分比（8500=8.5折）");
        }
    }
}
