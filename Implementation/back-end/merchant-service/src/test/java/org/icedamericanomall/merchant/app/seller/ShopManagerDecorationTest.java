package org.icedamericanomall.merchant.app.seller;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.icedamericanomall.merchant.adapter.request.ShopDecorationReq;
import org.icedamericanomall.merchant.adapter.response.ShopDecorationVO;
import org.icedamericanomall.merchant.adapter.response.ShopVO;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.model.shop.Shop;
import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;
import org.icedamericanomall.merchant.domain.model.shop.ShopStatusEnum;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 店铺装修编排单元测试（S1c/S1d + SH1 装配，ADR-048）——
 * 归属校验复用（403）、未装修空对象、保存校验前置、公开店铺页 decoration 两态装配。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShopManager 店铺装修编排单元测试")
class ShopManagerDecorationTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ShopDecorationRepository shopDecorationRepository;

    /** 粉丝数读模型端口：本测试只覆盖装修装配（未打桩 → 返回 null Map，粉丝数字段留空）。 */
    @Mock
    private org.icedamericanomall.merchant.app.port.StoreFollowStore storeFollowStore;

    @InjectMocks
    private ShopManager shopManager;

    private Merchant merchant(Long id, Long userId) {
        return Merchant.builder().id(id).userId(userId).status(MerchantStatusEnum.NORMAL).build();
    }

    private Shop shop(Long id, Long merchantId, ShopStatusEnum status) {
        return Shop.builder()
                .id(id).merchantId(merchantId)
                .shopName("冰美式旗舰店").shopLogo("logo.png")
                .status(status)
                .build();
    }

    private void givenOwnedShop(Long userId, Long shopId, Long merchantId) {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant(merchantId, userId)));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop(shopId, merchantId, ShopStatusEnum.NORMAL)));
    }

    /** 店铺归属他人：当前用户（callerMerchantId 名下）操作 ownerMerchantId 名下的店铺。 */
    private void givenShopOwnedByOther(Long userId, Long shopId, Long callerMerchantId, Long ownerMerchantId) {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant(callerMerchantId, userId)));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop(shopId, ownerMerchantId, ShopStatusEnum.NORMAL)));
    }

    private ShopDecorationReq req(String announcement, List<String> banners, List<Long> hotProductIds) {
        ShopDecorationReq req = new ShopDecorationReq();
        req.setAnnouncement(announcement);
        req.setBanners(banners);
        req.setHotProductIds(hotProductIds);
        return req;
    }

    @Test
    @DisplayName("保存装修 — 店铺归属当前用户：领域归一化后 UPSERT（推荐位去重保序）")
    void shouldSaveDecoration_whenShopOwned() {
        givenOwnedShop(100L, 5L, 1L);

        shopManager.saveDecoration(100L, 5L,
                req("  欢迎光临  ", List.of("https://a.png"), List.of(3L, 1L, 3L)));

        ArgumentCaptor<ShopDecoration> captor = ArgumentCaptor.forClass(ShopDecoration.class);
        verify(shopDecorationRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getShopId());
        assertEquals("欢迎光临", captor.getValue().getAnnouncement());
        assertEquals(List.of("https://a.png"), captor.getValue().getBanners());
        assertEquals(List.of(3L, 1L), captor.getValue().getHotProductIds());
    }

    @Test
    @DisplayName("保存装修 — 店铺归属其他商家 → FORBIDDEN，不落库")
    void shouldThrowForbidden_whenSavingDecorationOfOthersShop() {
        givenShopOwnedByOther(100L, 5L, 1L, 2L);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> shopManager.saveDecoration(100L, 5L, req(null, null, null)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(shopDecorationRepository, never()).save(any(ShopDecoration.class));
    }

    @Test
    @DisplayName("保存装修 — 店铺不存在 → SHOP_NOT_FOUND，不落库")
    void shouldThrowShopNotFound_whenSavingDecorationOfUnknownShop() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findById(9L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> shopManager.saveDecoration(100L, 9L, req(null, null, null)));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
        verify(shopDecorationRepository, never()).save(any(ShopDecoration.class));
    }

    @Test
    @DisplayName("保存装修 — 轮播超 5 张 → 400 PARAM_ERROR（轮播图最多 5 张），不落库")
    void shouldThrowParamError_whenSavingMoreThanFiveBanners() {
        givenOwnedShop(100L, 5L, 1L);
        List<String> sixBanners = List.of(
                "https://1.png", "https://2.png", "https://3.png",
                "https://4.png", "https://5.png", "https://6.png");

        BizException ex = assertThrows(BizException.class,
                () -> shopManager.saveDecoration(100L, 5L, req(null, sixBanners, null)));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("轮播图最多 5 张", ex.getDisplayMessage());
        verify(shopDecorationRepository, never()).save(any(ShopDecoration.class));
    }

    @Test
    @DisplayName("查询装修 — 未装修返回空对象（公告空值 + 空数组）")
    void shouldReturnEmptyVO_whenNotDecorated() {
        givenOwnedShop(100L, 5L, 1L);
        when(shopDecorationRepository.findByShopId(5L)).thenReturn(Optional.empty());

        ShopDecorationVO vo = shopManager.getDecoration(100L, 5L);

        assertNull(vo.getAnnouncement());
        assertTrue(vo.getBanners().isEmpty());
        assertTrue(vo.getHotProductIds().isEmpty());
    }

    @Test
    @DisplayName("查询装修 — 已装修返回装修内容")
    void shouldReturnDecorationVO_whenDecorated() {
        givenOwnedShop(100L, 5L, 1L);
        when(shopDecorationRepository.findByShopId(5L)).thenReturn(Optional.of(ShopDecoration.of(5L,
                "欢迎光临", List.of("https://a.png"), List.of(11L))));

        ShopDecorationVO vo = shopManager.getDecoration(100L, 5L);

        assertEquals("欢迎光临", vo.getAnnouncement());
        assertEquals(List.of("https://a.png"), vo.getBanners());
        assertEquals(List.of(11L), vo.getHotProductIds());
    }

    @Test
    @DisplayName("查询装修 — 店铺归属其他商家 → FORBIDDEN")
    void shouldThrowForbidden_whenGettingDecorationOfOthersShop() {
        givenShopOwnedByOther(100L, 5L, 1L, 2L);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> shopManager.getDecoration(100L, 5L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("公开店铺页 — 已装修：ShopVO.decoration 装配装修内容")
    void shouldAssembleDecoration_whenShopVisibleAndDecorated() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopDecorationRepository.findByShopId(5L)).thenReturn(Optional.of(ShopDecoration.of(5L,
                "欢迎光临", List.of("https://a.png"), List.of(11L))));

        ShopVO vo = shopManager.getVisibleShop(5L);

        assertEquals("欢迎光临", vo.getDecoration().getAnnouncement());
        assertEquals(List.of("https://a.png"), vo.getDecoration().getBanners());
        assertEquals(List.of(11L), vo.getDecoration().getHotProductIds());
    }

    @Test
    @DisplayName("公开店铺页 — 未装修：ShopVO.decoration 为 null")
    void shouldAssembleNullDecoration_whenShopVisibleButNotDecorated() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, ShopStatusEnum.NORMAL)));
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopDecorationRepository.findByShopId(5L)).thenReturn(Optional.empty());

        ShopVO vo = shopManager.getVisibleShop(5L);

        assertNull(vo.getDecoration());
    }
}
