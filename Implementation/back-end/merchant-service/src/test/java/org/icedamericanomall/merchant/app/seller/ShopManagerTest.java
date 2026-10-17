package org.icedamericanomall.merchant.app.seller;

import org.icedamericanomall.common.enums.ErrorCategory;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @DisplayName("公开店铺页 — 店铺不存在 → SHOP_NOT_FOUND（纯 not-found，不是状态/越权语义）")
    void shouldThrowShopNotFound_whenShopAbsent() {
        when(shopRepository.findById(5L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> shopManager.getVisibleShop(5L));

        assertEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode());
        // 拆分守卫：本分支只许「不存在」，不得串到状态不允许（批次 ③ 的语义边界）
        assertNotEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode(),
                "「不存在」不得复用「状态不允许」的码");
    }

    /**
     * 拆分守卫（批次 ③「拆双义错误码」）：<b>店铺存在但状态不允许</b> → {@code RULE}（200 + body code），
     * <b>不再是</b> {@code SHOP_NOT_FOUND}。
     *
     * <p><b>改前必失败</b>：改前本分支抛 {@code SHOP_NOT_FOUND(3015)}
     * （改前用例名 {@code shouldThrowShopNotFound_whenShopNotNormal}），
     * 断言 {@code assertEquals(ErrorCode.SHOP_NOT_FOUND, ...)} 会通过 —— 本用例正是把
     * 「两种语义共用一个码」钉死的哨兵：拆分前它<b>必然</b>红（拿到的是 3015）。
     *
     * <p><b>状态码口径</b>：本批<b>只拆语义不改状态码</b>（{@code 3015} 仍是 200，由批次 ① 归位 404）；
     * 因此这里断言的是<b>类别</b>而非具体数字 —— {@code ErrorCategory.of(code)} 由
     * {@code ErrorCode.httpStatus} <b>派生</b>（不手填 category），{@code RULE} 即「200 + 业务码」。
     */
    @Test
    @DisplayName("公开店铺页 — 店铺存在但状态非正常（审核中/冻结）→ RULE 拒绝（200 + 业务码），不是 SHOP_NOT_FOUND")
    void shouldRejectAsBusinessRule_whenShopStatusNotNormal() {
        for (ShopStatusEnum status : new ShopStatusEnum[]{ShopStatusEnum.PENDING, ShopStatusEnum.FROZEN}) {
            when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 1L, status)));

            BizException ex = assertThrows(BizException.class, () -> shopManager.getVisibleShop(5L));

            // ① 语义已独立：不再是 not-found
            assertNotEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode(),
                    "状态不允许不是「店铺不存在」（批次 ③ 拆双义错误码）: status=" + status);
            // ② 承载归属：业务规则拒绝 = 200 + body code（09 §三·六 第四类），由状态码派生类别
            assertEquals(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, ex.getErrorCode());
            assertEquals(ErrorCategory.RULE, ErrorCategory.of(ex.getErrorCode()),
                    "状态不允许属 RULE（200 + 业务码），不是 CLIENT（4xx）");
            assertTrue(ex.getErrorCode().getHttpStatus().is2xxSuccessful(),
                    "本批不改状态码：该分支仍是 HTTP 200");
            // ③ 文案必须能区分「被拆出去的状态语义」与「店铺不存在」
            assertNotEquals(ErrorCode.SHOP_NOT_FOUND.getMessage(), ex.getDisplayMessage());
        }
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
    @DisplayName("更新店铺 — 店铺归属其他商家 → FORBIDDEN(403)，不得用 not-found 掩盖，不落库")
    void shouldThrowForbidden_whenShopBelongsToOtherMerchant() {
        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(merchant(1L, 100L)));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop(5L, 2L, ShopStatusEnum.NORMAL)));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> shopManager.updateMyShop(100L, 5L, new UpdateShopReq()));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        // 越权必须是 403（09 §三·六「未认证/无权限」），不得退化成 not-found
        assertNotEquals(ErrorCode.SHOP_NOT_FOUND, ex.getErrorCode(),
                "越权不得用「店铺不存在」掩盖（本仓无「反枚举」产品要求）");
        assertEquals(ErrorCategory.CLIENT, ErrorCategory.of(ex.getErrorCode()));
        assertTrue(ex.getErrorCode().getHttpStatus().is4xxClientError());
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
