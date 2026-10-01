package org.icedamericanomall.user.app.seller;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.repository.SellerRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 店铺公开页编排（应用层）—— 商家公开信息查询（Controller 只做参数解析 + 委托）。
 *
 * <p>返回形状为店铺页契约（{@code Map} 键名即 JSON key，与迁移前一致），
 * 仅返回正常状态（NORMAL）的商家。
 */
@Component
@RequiredArgsConstructor
public class ShopManager {

    private final SellerRepository sellerRepository;

    /**
     * 查询公开店铺信息。
     *
     * @param sellerId 商家用户 ID（对外的 sellerId 口径 = seller.user_id）
     * @return 店铺信息；不存在 / 非正常状态返回 null
     */
    public Map<String, Object> getShopInfo(Long sellerId) {
        Seller shop = sellerRepository.findActiveByUserId(sellerId).orElse(null);
        if (shop == null) {
            return null;
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sellerId", shop.getUserId());
        info.put("shopName", shop.getShopName());
        info.put("shopLogo", shop.getShopLogo());
        info.put("contactPhone", shop.getContactPhone());
        info.put("address", shop.getProvince() + shop.getCity() + shop.getDistrict());
        return info;
    }
}
