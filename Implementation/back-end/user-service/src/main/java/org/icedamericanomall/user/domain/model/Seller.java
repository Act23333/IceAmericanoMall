package org.icedamericanomall.user.domain.model;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 商家聚合根（纯 POJO）。
 *
 * <p>状态机：{@code PENDING(0)} → {@code NORMAL(1)} / {@code FROZEN(2)}；
 * 冻结 / 解冻为幂等迁移。是否可下单/上架等"是否有效商家"的判断由 {@link #isActive()} 提供，
 * 跨聚合不变量（一个用户至多一个商家）由领域服务 {@code SellerRegistrar} 保证。
 */
@Data
@Builder
public class Seller {

    /** 技术主键 */
    private Long id;

    /** 关联用户 ID（跨服务 user_id 语义，技术主键） */
    private Long userId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    private SellerStatusEnum status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /** 新商家入驻申请（审核中状态）。 */
    public static Seller apply(Long userId) {
        return Seller.builder()
                .userId(userId)
                .status(SellerStatusEnum.PENDING)
                .build();
    }

    // ==================== 业务方法 ====================

    /** 入驻审核通过（幂等：已通过则直接返回）。 */
    public void approve() {
        if (this.status == SellerStatusEnum.NORMAL) {
            return;
        }
        this.status = SellerStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    /** 冻结（幂等）。 */
    public void freeze() {
        if (this.status == SellerStatusEnum.FROZEN) {
            return;
        }
        this.status = SellerStatusEnum.FROZEN;
        this.updateTime = LocalDateTime.now();
    }

    /** 解冻（仅已冻结商家可解冻）。 */
    public void unfreeze() {
        assertStatus(SellerStatusEnum.FROZEN, "只有已冻结的商家可以解冻");
        this.status = SellerStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    /** null-safe 部分更新店铺信息。 */
    public void updateShopInfo(String shopName, String shopLogo, String contactPhone,
                               String province, String city, String district, String detailAddress) {
        if (shopName != null) this.shopName = shopName;
        if (shopLogo != null) this.shopLogo = shopLogo;
        if (contactPhone != null) this.contactPhone = contactPhone;
        if (province != null) this.province = province;
        if (city != null) this.city = city;
        if (district != null) this.district = district;
        if (detailAddress != null) this.detailAddress = detailAddress;
        this.updateTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == SellerStatusEnum.NORMAL;
    }

    public boolean isPending() {
        return this.status == SellerStatusEnum.PENDING;
    }

    public boolean isFrozen() {
        return this.status == SellerStatusEnum.FROZEN;
    }

    // ==================== 内部校验 ====================

    private void assertStatus(SellerStatusEnum expected, String msg) {
        if (this.status != expected) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    msg + ", 当前状态: " + this.status);
        }
    }
}
