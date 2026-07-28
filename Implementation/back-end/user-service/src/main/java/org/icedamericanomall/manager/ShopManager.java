package org.icedamericanomall.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.SellerEntity;
import org.icedamericanomall.mapper.SellerMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * V4.0 DDD: 店铺编排 Manager（Application层）。
 * 职责: 商家信息查询 —— Controller只做参数解析+委托。
 */
@Component
@RequiredArgsConstructor
public class ShopManager {

    private final SellerMapper sellerMapper;

    public Map<String, Object> getShopInfo(Long sellerId) {
        SellerEntity shop = sellerMapper.selectOne(
                new LambdaQueryWrapper<SellerEntity>()
                        .eq(SellerEntity::getUserId, sellerId)
                        .eq(SellerEntity::getStatus, 1));
        if (shop == null) return null;

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sellerId", shop.getUserId());
        info.put("shopName", shop.getShopName());
        info.put("shopLogo", shop.getShopLogo());
        info.put("contactPhone", shop.getContactPhone());
        info.put("address", shop.getProvince() + shop.getCity() + shop.getDistrict());
        return info;
    }
}
