package org.icedamericanomall.marketing.domain.model.groupbuy;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.domain.enums.GroupBuyActivityStatusEnum;

import java.time.LocalDateTime;

/**
 * 拼团活动聚合（纯 POJO）—— {@code group_buy_activity} 表（F13 拼团，V5.14，ADR-049）。
 *
 * <p>店铺级活动，绑定单一 SKU（拼团仅该 SKU 参与，库存沿用 {@code sku.stock} 与既有
 * 原子扣减 ADR-020，不单独建模）；{@code groupPrice} 为拼团价（分，&gt;0），拼团单
 * 不叠加券/促销/会员（与秒杀同口径，ADR-043/ADR-049）。金额一律整数分。
 *
 * <p>{@code version} 为乐观锁列：编辑时随聚合携带，冲突由 MyBatis-Plus 拦截器拦截。
 *
 * <p><b>状态口径</b>：活动状态由 {@code startTime}/{@code endTime} 时间窗实时推导
 * （1未开始/2进行中/3已结束，{@link #deriveStatus}），落库 {@code status} 仅为写入时刻快照；
 * 管理端守卫（已结束禁编 / 进行中禁删）与 I52 参团校验均不信任快照（复用 AD48-50 先例）。
 */
@Data
@Builder
public class GroupBuyActivity {

    /** 成团人数下限（≥2，表注释与契约 AD54 口径；团长自占一位，1 人无拼团语义）。 */
    public static final int MIN_REQUIRED_MEMBERS = 2;

    /** 成团时限下限（小时，≥1，契约 AD54 口径）。 */
    public static final int MIN_TIME_LIMIT_HOURS = 1;

    private Long id;
    private String name;
    private Long sellerId;
    private Long spuId;
    private Long skuId;
    /** 拼团价（分，>0；快照进拼团单 subTotal） */
    private Integer groupPrice;
    /** 成团人数（≥2） */
    private Integer requiredMembers;
    /** 成团时限（小时，开团后超时未满员即失败） */
    private Integer timeLimitHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;

    /**
     * 按时间窗推导活动状态（契约 AD54 口径）：未开始=1 / 进行中=2 / 已结束=3。
     *
     * @param now 判定时刻
     * @return 状态码，见 {@link GroupBuyActivityStatusEnum}
     */
    public int deriveStatus(LocalDateTime now) {
        if (now.isBefore(this.startTime)) {
            return GroupBuyActivityStatusEnum.PENDING.getCode();
        }
        if (now.isAfter(this.endTime)) {
            return GroupBuyActivityStatusEnum.ENDED.getCode();
        }
        return GroupBuyActivityStatusEnum.ACTIVE.getCode();
    }

    /**
     * 校验时间窗有序（endTime 严格晚于 startTime）—— 聚合不变式（AD54/AD55 字段口径）。
     *
     * @throws BizException 时间窗非法（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public void ensureTimeWindowOrdered() {
        if (this.startTime == null || this.endTime == null || !this.endTime.isAfter(this.startTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
    }

    /**
     * 校验拼团字段合法 —— 聚合不变式（JSR-303 之外的领域侧防线）：
     * 拼团价 &gt; 0、成团人数 ≥ {@value MIN_REQUIRED_MEMBERS}、成团时限 ≥ {@value MIN_TIME_LIMIT_HOURS} 小时。
     *
     * @throws BizException 任一字段越界（{@link ErrorCode#PARAM_ERROR}，HTTP 400）
     */
    public void ensureFieldsLegal() {
        if (this.groupPrice == null || this.groupPrice <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "拼团价必须大于0（分）");
        }
        if (this.requiredMembers == null || this.requiredMembers < MIN_REQUIRED_MEMBERS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成团人数必须不小于" + MIN_REQUIRED_MEMBERS);
        }
        if (this.timeLimitHours == null || this.timeLimitHours < MIN_TIME_LIMIT_HOURS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "成团时限必须不小于" + MIN_TIME_LIMIT_HOURS + "小时");
        }
    }
}
