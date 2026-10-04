package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券模板表映射对象（PO）—— {@code coupon} 表，仅存在于基础设施持久化层。
 *
 * <p>原 {@code CouponPO} 上的领域行为（claim / rollbackClaim / calculateDiscount / publish /
 * pause / terminate / isActive…）与状态/折扣类型常量已迁至 {@code domain.model.coupon.Coupon}
 * 与 {@code domain.enums.CouponStatusEnum}（DDD 分层：MyBatis-Plus 注解不得出现在 domain 包），
 * 本类只留持久化字段与审计填充。
 */
@Data
@TableName("coupon")
public class CouponPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String couponId;
    private String name;

    /** DEPRECATED（V4.0）历史字段：折扣类型优先读 {@code discountType}。 */
    private Integer type;

    private Integer value;
    private Integer minAmount;
    private Integer totalQty;
    private Integer issuedQty;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Long sellerId;
    private Integer discountType;
    private Integer couponCategory;
    private Integer grantType;
    private Integer stockType;
    private Integer grabType;
    private Integer priceInCents;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
