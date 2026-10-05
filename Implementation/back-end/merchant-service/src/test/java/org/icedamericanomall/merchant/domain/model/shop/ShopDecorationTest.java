package org.icedamericanomall.merchant.domain.model.shop;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 店铺装修领域校验单元测试 —— 创建即校验：公告 ≤200、轮播 ≤5 且 http(s)、
 * 推荐位 ≤8 且去重保序（ADR-048）。纯 POJO，不依赖任何框架。
 */
@DisplayName("ShopDecoration 领域校验单元测试")
class ShopDecorationTest {

    @Test
    @DisplayName("创建装修 — 归一化：公告去首尾空白、轮播逐条去空白、推荐位去重保序")
    void shouldNormalizeAndDedup_whenOfCreated() {
        ShopDecoration decoration = ShopDecoration.of(5L, "  欢迎光临  ",
                List.of(" https://a.png ", "https://b.png"),
                List.of(3L, 1L, 3L, 2L, 1L));

        assertEquals("欢迎光临", decoration.getAnnouncement());
        assertEquals(List.of("https://a.png", "https://b.png"), decoration.getBanners());
        assertEquals(List.of(3L, 1L, 2L), decoration.getHotProductIds(), "去重且保持首次出现顺序");
        assertEquals(5L, decoration.getShopId());
    }

    @Test
    @DisplayName("创建装修 — 未传字段：公告为 null、两个列表为空数组")
    void shouldReturnBlankFields_whenInputNull() {
        ShopDecoration decoration = ShopDecoration.of(5L, null, null, null);

        assertNull(decoration.getAnnouncement());
        assertTrue(decoration.getBanners().isEmpty());
        assertTrue(decoration.getHotProductIds().isEmpty());
    }

    @Test
    @DisplayName("创建装修 — 轮播超 5 张 → PARAM_ERROR（轮播图最多 5 张）")
    void shouldThrowParamError_whenBannersExceedFive() {
        List<String> sixBanners = List.of(
                "https://1.png", "https://2.png", "https://3.png",
                "https://4.png", "https://5.png", "https://6.png");

        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(5L, null, sixBanners, null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("轮播图最多 5 张", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("创建装修 — 轮播含非 http(s) 链接 → PARAM_ERROR")
    void shouldThrowParamError_whenBannerUrlNotHttp() {
        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(5L, null, List.of("ftp://a.png"), null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("轮播图地址必须为 http(s) 链接", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("创建装修 — 推荐位去重后超 8 个 → PARAM_ERROR（推荐商品位最多 8 个）")
    void shouldThrowParamError_whenHotProductsExceedEight() {
        List<Long> nineIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(5L, null, null, nineIds));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("推荐商品位最多 8 个", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("创建装修 — 公告超 200 字 → PARAM_ERROR（公告最多 200 字）")
    void shouldThrowParamError_whenAnnouncementTooLong() {
        String tooLong = "长".repeat(201);

        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(5L, tooLong, null, null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("公告最多 200 字", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("创建装修 — 空白公告归一化为 null")
    void shouldNullify_whenAnnouncementBlank() {
        ShopDecoration decoration = ShopDecoration.of(5L, "   ", null, null);

        assertNull(decoration.getAnnouncement());
    }

    @Test
    @DisplayName("创建装修 — shopId 缺失 → PARAM_ERROR")
    void shouldThrowParamError_whenShopIdMissing() {
        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(null, null, null, null));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("未装修占位 — blank()：公告为 null、两个列表为空数组")
    void shouldReturnBlank_whenNotDecorated() {
        ShopDecoration blank = ShopDecoration.blank();

        assertNull(blank.getAnnouncement());
        assertTrue(blank.getBanners().isEmpty());
        assertTrue(blank.getHotProductIds().isEmpty());
    }
}
