package org.icedamericanomall.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家聚合根 — COLA 充血模型 V5.0
 *
 * <pre>
 * 不变量:
 *   1. 状态流转: PENDING(0) → NORMAL(1) / FROZEN(2) / REJECTED
 *   2. 冻结的商家不能操作商品和订单
 * </pre>
 */
@Getter
@TableName("seller")
public class SellerEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int PENDING = 0;
    public static final int NORMAL  = 1;
    public static final int FROZEN  = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String shopName;
    private String shopLogo;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 领域行为 ====================

    /** 审核通过 */
    public void approve() {
        assertStatus(PENDING, "只有审核中的商家可以审批");
        this.status = NORMAL;
    }

    /** 冻结 */
    public void freeze() {
        if (this.status == FROZEN) return;
        this.status = FROZEN;
    }

    /** 解冻 */
    public void unfreeze() {
        assertStatus(FROZEN, "只有已冻结的商家可以解冻");
        this.status = NORMAL;
    }

    /** 更新店铺信息 */
    public void updateShopInfo(String shopName, String shopLogo, String contactPhone,
                               String province, String city, String district, String detailAddress) {
        if (shopName != null) this.shopName = shopName;
        if (shopLogo != null) this.shopLogo = shopLogo;
        if (contactPhone != null) this.contactPhone = contactPhone;
        if (province != null) this.province = province;
        if (city != null) this.city = city;
        if (district != null) this.district = district;
        if (detailAddress != null) this.detailAddress = detailAddress;
    }

    // ==================== 查询方法 ====================

    public boolean isActive()  { return this.status == NORMAL; }
    public boolean isPending() { return this.status == PENDING; }
    public boolean isFrozen()  { return this.status == FROZEN; }

    // ==================== 内部校验 ====================

    private void assertStatus(int expected, String msg) {
        if (this.status == null || this.status != expected)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, msg + ", 当前状态: " + this.status);
    }

    // ==================== 持久层 setter（MyBatis-Plus 兼容） ====================

    public void setId(Long v) { this.id = v; }
    public void setUserId(Long v) { this.userId = v; }
    public void setShopName(String v) { this.shopName = v; }
    public void setShopLogo(String v) { this.shopLogo = v; }
    public void setContactPhone(String v) { this.contactPhone = v; }
    public void setProvince(String v) { this.province = v; }
    public void setCity(String v) { this.city = v; }
    public void setDistrict(String v) { this.district = v; }
    public void setDetailAddress(String v) { this.detailAddress = v; }
    public void setStatus(Integer v) { this.status = v; }
    public void setCreateTime(LocalDateTime v) { this.createTime = v; }
    public void setUpdateTime(LocalDateTime v) { this.updateTime = v; }
}
