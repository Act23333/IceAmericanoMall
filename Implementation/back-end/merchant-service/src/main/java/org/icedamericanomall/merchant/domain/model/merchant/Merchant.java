package org.icedamericanomall.merchant.domain.model.merchant;

import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 商家聚合根（纯 POJO）—— 只承载"商家身份 + 生命周期"。
 *
 * <p>店铺资料（店名/Logo/联系电话/地址）已于 ADR-019 拆到 {@link Shop} 聚合（1:N），
 * 本聚合不再持有店铺字段；跨聚合协作（申请建店、审核同步生效）在应用层编排。
 *
 * <p>状态机：{@code PENDING(0)} → {@code NORMAL(1)} / {@code FROZEN(2)}；
 * 冻结 / 解冻为幂等迁移，外部只能经实体方法迁移（禁止直写状态列）。
 * 是否可下单/上架等"是否有效商家"的判断由 {@link #isActive()} 提供，
 * 跨聚合不变量（一个用户至多一个商家）由领域服务 {@code MerchantRegistrar} 保证。
 */
@Data
@Builder
public class Merchant {

    /** 技术主键 */
    private Long id;

    /** 关联用户 ID（跨服务 user_id 语义，技术主键） */
    private Long userId;

    private MerchantStatusEnum status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /** 新商家入驻申请（审核中状态）。 */
    public static Merchant apply(Long userId) {
        return Merchant.builder()
                .userId(userId)
                .status(MerchantStatusEnum.PENDING)
                .build();
    }

    // ==================== 业务方法 ====================

    /** 入驻审核通过（幂等：已通过则直接返回）。 */
    public void approve() {
        if (this.status == MerchantStatusEnum.NORMAL) {
            return;
        }
        this.status = MerchantStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    /** 冻结（幂等）。 */
    public void freeze() {
        if (this.status == MerchantStatusEnum.FROZEN) {
            return;
        }
        this.status = MerchantStatusEnum.FROZEN;
        this.updateTime = LocalDateTime.now();
    }

    /** 解冻（仅已冻结商家可解冻）。 */
    public void unfreeze() {
        assertStatus(MerchantStatusEnum.FROZEN, "只有已冻结的商家可以解冻");
        this.status = MerchantStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == MerchantStatusEnum.NORMAL;
    }

    public boolean isPending() {
        return this.status == MerchantStatusEnum.PENDING;
    }

    public boolean isFrozen() {
        return this.status == MerchantStatusEnum.FROZEN;
    }

    // ==================== 内部校验 ====================

    private void assertStatus(MerchantStatusEnum expected, String msg) {
        if (this.status != expected) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    msg + ", 当前状态: " + this.status);
        }
    }
}
