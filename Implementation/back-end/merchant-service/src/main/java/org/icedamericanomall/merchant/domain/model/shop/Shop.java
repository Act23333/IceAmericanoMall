package org.icedamericanomall.merchant.domain.model.shop;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;

/**
 * 店铺聚合根（纯 POJO）—— 商家（1）对店铺（N）的拆分模型（ADR-019）。
 *
 * <p>归属表达：{@link #merchantId} 只持有商家聚合的技术主键（聚合间只引用 ID，不持对象引用），
 * 一个商家至少有一个默认店铺（取 id 最小者），"默认店铺兼容解析"见
 * {@code ShopManager#resolveShopId}。
 *
 * <p>状态机：{@code PENDING(0)} → {@code NORMAL(1)} / {@code FROZEN(2)}；
 * 冻结 / 解冻为幂等迁移，迁移合法性内聚在实体方法内（不再由应用层直写状态列）。
 */
@Data
@Builder
public class Shop {

    /** 技术主键（对外的 shopId 口径） */
    private Long id;

    /** 所属商家 ID（{@code seller.id}，聚合间只引用 ID） */
    private Long merchantId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    private ShopStatusEnum status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 开店（入驻申请时创建默认店铺，初始状态审核中）。
     *
     * <p>创建即校验：库表 NOT NULL 的资料列在此统一做非空校验，
     * 避免脏数据落到持久化层再以 SQL 异常暴露。
     *
     * @throws BizException 必填资料缺失（PARAM_ERROR）
     */
    public static Shop open(String shopName, String shopLogo, String contactPhone,
                            String province, String city, String district, String detailAddress) {
        assertNotBlank(shopName, "店铺名称");
        assertNotBlank(contactPhone, "联系电话");
        assertNotBlank(province, "发货省");
        assertNotBlank(city, "发货市");
        assertNotBlank(district, "发货区/县");
        assertNotBlank(detailAddress, "详细地址");
        return Shop.builder()
                .shopName(shopName)
                .shopLogo(shopLogo)
                .contactPhone(contactPhone)
                .province(province)
                .city(city)
                .district(district)
                .detailAddress(detailAddress)
                .status(ShopStatusEnum.PENDING)
                .build();
    }

    // ==================== 业务方法 ====================

    /**
     * 回填归属商家（商家先落库拿到技术主键后调用；同 ID 重复回填幂等）。
     *
     * @throws BizException 店铺已归属其他商家（PARAM_ERROR）
     */
    public void assignMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "所属商家不能为空");
        }
        if (this.merchantId != null && !this.merchantId.equals(merchantId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "店铺已归属其他商家");
        }
        this.merchantId = merchantId;
    }

    /** 审核通过（幂等：已正常则直接返回）。 */
    public void approve() {
        if (this.status == ShopStatusEnum.NORMAL) {
            return;
        }
        this.status = ShopStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    /** 冻结（幂等）。 */
    public void freeze() {
        if (this.status == ShopStatusEnum.FROZEN) {
            return;
        }
        this.status = ShopStatusEnum.FROZEN;
        this.updateTime = LocalDateTime.now();
    }

    /** 解冻（仅已冻结店铺可解冻）。 */
    public void unfreeze() {
        if (this.status != ShopStatusEnum.FROZEN) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "只有已冻结的店铺可以解冻, 当前状态: " + this.status);
        }
        this.status = ShopStatusEnum.NORMAL;
        this.updateTime = LocalDateTime.now();
    }

    /** null-safe 部分更新店铺资料（null 表示不修改该字段）。 */
    public void updateProfile(String shopName, String shopLogo, String contactPhone,
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

    /** 是否正常营业（公开店铺页可见的唯一条件）。 */
    public boolean isNormal() {
        return this.status == ShopStatusEnum.NORMAL;
    }

    // ==================== 内部校验 ====================

    private static void assertNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, field + "不能为空");
        }
    }
}
