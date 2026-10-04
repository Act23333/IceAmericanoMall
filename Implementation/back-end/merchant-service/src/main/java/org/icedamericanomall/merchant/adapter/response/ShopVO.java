package org.icedamericanomall.merchant.adapter.response;

import lombok.Data;
import org.icedamericanomall.merchant.domain.model.shop.Shop;

import java.io.Serial;
import java.io.Serializable;

/**
 * 店铺视图对象（对外统一店铺契约，ADR-019）。
 *
 * <p>字段口径：{@code shopId}=店铺主键；{@code sellerId}=所属商家的用户 ID
 * （下游与前端沿用的 sellerId 口径 = 商家 user_id，Phase A 不变）；
 * {@code address} 为省市区摘要（不含详细地址，避免公开页泄漏发货明细）。
 */
@Data
public class ShopVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 店铺 ID */
    private Long shopId;

    /** 商家用户 ID（对外 sellerId 口径） */
    private Long sellerId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    /** 省市区摘要 */
    private String address;

    /** 状态：0=审核中，1=正常，2=冻结 */
    private Integer status;

    /** 领域模型 → 视图对象。 */
    public static ShopVO from(Shop shop, Long sellerUserId) {
        ShopVO vo = new ShopVO();
        vo.setShopId(shop.getId());
        vo.setSellerId(sellerUserId);
        vo.setShopName(shop.getShopName());
        vo.setShopLogo(shop.getShopLogo());
        vo.setContactPhone(shop.getContactPhone());
        vo.setAddress(address(shop));
        vo.setStatus(shop.getStatus() == null ? null : shop.getStatus().getCode());
        return vo;
    }

    private static String address(Shop shop) {
        return nullToEmpty(shop.getProvince()) + nullToEmpty(shop.getCity()) + nullToEmpty(shop.getDistrict());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
