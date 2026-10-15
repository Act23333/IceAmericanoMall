package org.icedamericanomall.merchant.app.seller;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.merchant.adapter.request.UpdateShopReq;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.app.port.StoreFollowStore;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.icedamericanomall.merchant.domain.repository.ShopDecorationRepository;
import org.icedamericanomall.merchant.domain.repository.ShopRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店铺查询与维护编排单元测试 —— 公开页可见性、遗留 sellerId → 默认店铺解析、
 * 店铺列表、归属校验与资料更新、粉丝数回填（批量 + fail-open）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopManager 店铺编排单元测试")
class ShopManagerTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ShopDecorationRepository shopDecorationRepository;

    @Mock
    private StoreFollowStore storeFollowStore;

    @InjectMocks
    private ShopManager shopManager;

    private Merchant merchant(Long id, Long userId) {
        return Merchant.builder().id(id).userId(userId).status(MerchantStatusEnum.NORMAL).build();
    }

    private Shop shop(Long id, Long merchantId, ShopStatusEnum status) {
        return Shop.builder()
                .id(id).merchantId(merchantId)
                .shopName("冰美式旗舰店").shopLogo("logo.png")
                .contactPhone("138****8000")
                .province("上海市").city("上海市").district("静安区")
                .detailAddress("南京西路 100 号")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("公开店铺页 — 正常状态店铺返回 ShopVO（shopId + sellerId=商家 user_id）")
    void shouldReturnShopVO_whenShopNormal() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));

        ShopVO vo = shopManager.getVisibleShop(5L);

        assertEquals(5L, vo.getShopId());
        assertEquals(100L, vo.getSellerId());
        assertEquals("冰美式旗舰店", vo.getShopName());
        assertEquals("上海市上海市静安区", vo.getAddress());
        assertEquals(ShopStatusEnum.NORMAL.getCode(), vo.getStatus());
    }

    @Test
    @DisplayName("公开店铺页 — 店铺不存在 → SHOP_NOT_FOUND")
    void shouldThrowShopNotFound_whenShopAbsent() {
        when(shopRepository.findById(5L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.getVisibleShop(5L));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("公开店铺页 — 非正常状态（审核中/冻结）→ SHOP_NOT_FOUND")
    void shouldThrowShopNotFound_whenShopNotNormal() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.PENDING)));

        BizException ex = assertThrows(BizException.class, () -> shopManager.getVisibleShop(5L));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("遗留兼容 — sellerId 解析到默认店铺（id 最小者）并返回 ShopVO")
    void shouldResolveDefaultShop_whenLegacySellerId() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findDefaultByMerchantId(1L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));

        ShopVO vo = shopManager.getLegacyShopBySellerId(100L);

        assertEquals(5L, vo.getShopId());
        assertEquals(100L, vo.getSellerId());
    }

    @Test
    @DisplayName("遗留兼容 — 商家不存在 / 无店铺 → SHOP_NOT_FOUND")
    void shouldThrowShopNotFound_whenLegacySellerHasNoShop() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.getLegacyShopBySellerId(100L));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("解析目标店铺 — shopId 与 sellerId 均未提供 → PARAM_ERROR")
    void shouldThrowParamError_whenBothKeysMissing() {
        BizException ex = assertThrows(BizException.class, () -> shopManager.resolveShopId(null, null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("解析目标店铺 — shopId 不存在 → SHOP_NOT_FOUND")
    void shouldThrowShopNotFound_whenResolveUnknownShopId() {
        when(shopRepository.findById(7L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.resolveShopId(7L, null));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("店铺列表 — 商家本人全部店铺（含审核中）")
    void shouldListMyShops_whenMerchantExists() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findByMerchantId(1L)).thenReturn(List.of(
                shop(5L, 1L, ShopStatusEnum.NORMAL),
                shop(6L, 1L, ShopStatusEnum.PENDING)));

        List<ShopVO> shops = shopManager.listMyShops(100L);

        assertEquals(2, shops.size());
        assertEquals(5L, shops.get(0).getShopId());
        assertEquals(100L, shops.get(0).getSellerId());
        assertEquals(6L, shops.get(1).getShopId());
    }

    @Test
    @DisplayName("店铺列表 — 尚未开通商家 → USER_NOT_FOUND")
    void shouldThrowUserNotFound_whenNotMerchant() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.listMyShops(100L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("更新店铺 — 归属当前用户：空白字段视为未提供（保留原值）")
    void shouldUpdateProfile_whenShopOwned() {
        Shop owned = shop(5L, 1L, ShopStatusEnum.NORMAL);
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(owned));

        UpdateShopReq req = new UpdateShopReq();
        req.setShopName("新店名");
        req.setShopLogo("   ");
        shopManager.updateMyShop(100L, 5L, req);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).updateProfile(captor.capture());
        assertEquals("新店名", captor.getValue().getShopName());
        assertEquals("logo.png", captor.getValue().getShopLogo(), "空白字符串视为未提供，保留原值");
        assertEquals("南京西路 100 号", captor.getValue().getDetailAddress(), "未提供的字段保持原值");
    }

    @Test
    @DisplayName("更新店铺 — 店铺不存在 → SHOP_NOT_FOUND，不落库")
    void shouldThrowShopNotFound_whenUpdatingUnknownShop() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findById(9L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> shopManager.updateMyShop(100L, 9L, new UpdateShopReq()));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
        verify(shopRepository, never()).updateProfile(any(Shop.class));
    }

    @Test
    @DisplayName("更新店铺 — 店铺归属其他商家 → FORBIDDEN，不落库")
    void shouldThrowForbidden_whenShopBelongsToOtherMerchant() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 2L, ShopStatusEnum.NORMAL)));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> shopManager.updateMyShop(100L, 5L, new UpdateShopReq()));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(shopRepository, never()).updateProfile(any(Shop.class));
    }

    @Test
    @DisplayName("遗留别名 — GET 默认店铺 / PUT 默认店铺（委托到指定店铺更新）")
    void shouldDelegateLegacyDefaultShopAlias() {
        Shop defaultShop = shop(5L, 1L, ShopStatusEnum.PENDING);
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findDefaultByMerchantId(1L)).thenReturn(Optional.of(defaultShop));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(defaultShop));

        assertEquals(5L, shopManager.getMyDefaultShop(100L).getShopId());

        UpdateShopReq req = new UpdateShopReq();
        req.setShopName("别名更新");
        shopManager.updateMyDefaultShop(100L, req);

        verify(shopRepository).updateProfile(defaultShop);
        assertEquals("别名更新", defaultShop.getShopName());
    }

    @Test
    @DisplayName("遗留别名 — 商家无默认店铺 → SHOP_NOT_FOUND")
    void shouldThrowShopNotFound_whenDefaultShopAbsent() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findDefaultByMerchantId(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.getMyDefaultShop(100L));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== 粉丝数回填（契约 follower_count，ADR-057） ====================

    @Test
    @DisplayName("公开店铺页 — 回填 follower_count（与 SF4 同源：Redis Set 基数）")
    void shouldFillFollowerCount_whenShopVisible() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(storeFollowStore.followerCounts(List.of(5L))).thenReturn(Map.of(5L, 128L));

        ShopVO vo = shopManager.getVisibleShop(5L);

        assertEquals(128L, vo.getFollowerCount());
        assertEquals("冰美式旗舰店", vo.getShopName(), "本体字段不受回填影响");
    }

    @Test
    @DisplayName("店铺列表 — 整批一次批量读（不逐店铺往返），逐店铺回填")
    void shouldBatchFollowerCounts_forShopList() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findByMerchantId(1L)).thenReturn(List.of(
                shop(5L, 1L, ShopStatusEnum.NORMAL),
                shop(6L, 1L, ShopStatusEnum.NORMAL)));
        when(storeFollowStore.followerCounts(List.of(5L, 6L))).thenReturn(Map.of(5L, 128L, 6L, 7L));

        List<ShopVO> shops = shopManager.listMyShops(100L);

        assertEquals(128L, shops.get(0).getFollowerCount());
        assertEquals(7L, shops.get(1).getFollowerCount());
        verify(storeFollowStore, times(1)).followerCounts(anyCollection());
    }

    @Test
    @DisplayName("店铺列表 — Redis 异常 → follower_count 为 null（fail-open，不回落 0），店铺信息照常返回")
    void shouldFailOpen_whenFollowerCountUnavailable() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findByMerchantId(1L)).thenReturn(List.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(storeFollowStore.followerCounts(anyCollection()))
                .thenThrow(new IllegalStateException("redis down"));

        List<ShopVO> shops = shopManager.listMyShops(100L);

        assertEquals(1, shops.size());
        assertNull(shops.get(0).getFollowerCount(), "Redis 不可用 → null（不是 0），店铺信息不受影响");
        assertEquals("冰美式旗舰店", shops.get(0).getShopName());
    }

    @Test
    @DisplayName("公开店铺页 — 粉丝数端口缺失该店铺 → follower_count 为 null")
    void shouldLeaveFollowerCountNull_whenMissingInPortResult() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(storeFollowStore.followerCounts(List.of(5L))).thenReturn(Map.of());

        assertNull(shopManager.getVisibleShop(5L).getFollowerCount());
    }
}
