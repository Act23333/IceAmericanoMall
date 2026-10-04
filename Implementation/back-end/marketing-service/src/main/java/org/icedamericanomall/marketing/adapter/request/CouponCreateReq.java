package org.icedamericanomall.marketing.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券模板创建请求（适配层入参）—— 管理后台 / 商家店铺券共用。
 *
 * <p>V4.0 五维模型：discountType × couponCategory × grantType × stockType × grabType。
 * 本类由原 {@code domain/dto/CouponCreateReq} 迁入：请求对象只服务于适配层入参绑定与校验。
 */
@Data
public class CouponCreateReq implements Serializable {
    private static final long serialVersionUID = 1L;

    private String couponId;
    @NotBlank
    private String name;
    @NotNull
    private Integer type;
    @NotNull
    @Min(1)
    private Integer value;
    @NotNull
    @Min(0)
    private Integer minAmount;
    @NotNull
    @Min(1)
    private Integer totalQty;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    private Integer status;

    // V4.0: 多维度优惠券模型（京东标准）
    @NotNull
    private Integer discountType;
    @NotNull
    private Integer couponCategory;
    @NotNull
    private Integer grantType;
    @NotNull
    private Integer stockType;
    @NotNull
    private Integer grabType;
    /** 付费券价格(分), grantType=PAID_PURCHASE时必填 */
    private Integer priceInCents;

    // V4.3: 适用范围 + 叠加规则
    private Integer scopeType;
    private String scopeValues;
    private Integer stackRule;
    private String stackGroup;
}
