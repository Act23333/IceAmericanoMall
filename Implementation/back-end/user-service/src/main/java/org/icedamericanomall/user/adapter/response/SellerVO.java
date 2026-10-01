package org.icedamericanomall.user.adapter.response;

import lombok.Data;
import org.icedamericanomall.user.domain.model.Seller;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家店铺视图对象（JSON 报文与重构前逐字段一致）。
 */
@Data
public class SellerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String shopName;

    private String shopLogo;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    /** 状态：0=审核中，1=正常，2=冻结 */
    private Integer status;

    private LocalDateTime createTime;

    /** 领域模型 → 视图对象。 */
    public static SellerVO from(Seller seller) {
        SellerVO vo = new SellerVO();
        vo.setId(seller.getId());
        vo.setUserId(seller.getUserId());
        vo.setShopName(seller.getShopName());
        vo.setShopLogo(seller.getShopLogo());
        vo.setContactPhone(seller.getContactPhone());
        vo.setProvince(seller.getProvince());
        vo.setCity(seller.getCity());
        vo.setDistrict(seller.getDistrict());
        vo.setDetailAddress(seller.getDetailAddress());
        vo.setStatus(seller.getStatus() == null ? null : seller.getStatus().getCode());
        vo.setCreateTime(seller.getCreateTime());
        return vo;
    }
}
