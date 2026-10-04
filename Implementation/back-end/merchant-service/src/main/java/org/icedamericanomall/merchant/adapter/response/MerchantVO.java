package org.icedamericanomall.merchant.adapter.response;

import lombok.Data;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商家视图对象（管理后台列表用）—— ADR-019 后只含商家身份 + 生命周期，
 * 店铺资料见 {@link ShopVO}。
 */
@Data
public class MerchantVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商家 ID（{@code seller.id}，管理端审核寻址口径） */
    private Long id;

    /** 商家用户 ID */
    private Long userId;

    /** 状态：0=审核中，1=正常，2=冻结 */
    private Integer status;

    private LocalDateTime createTime;

    /** 领域模型 → 视图对象。 */
    public static MerchantVO from(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        vo.setId(merchant.getId());
        vo.setUserId(merchant.getUserId());
        vo.setStatus(merchant.getStatus() == null ? null : merchant.getStatus().getCode());
        vo.setCreateTime(merchant.getCreateTime());
        return vo;
    }
}
