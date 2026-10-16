package org.icedamericanomall.merchant.domain.model.shop;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
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
 * 推荐位 ≤8 且去重保序、推荐位形态为 32-hex 业务键（ADR-048 + ADR-062 §决策 3/7）。
 * 纯 POJO，不依赖任何框架。
 */
@DisplayName("ShopDecoration 领域校验单元测试")
class ShopDecorationTest {

    /** 技术键 → SPU 业务键（32-hex 线格式；推荐位收口后的唯一合法形态）。 */
    private static String spuKey(long technicalId) {
        return String.format("%032x", technicalId);
    }

    @Test
    @DisplayName("创建装修 — 归一化：公告去首尾空白、轮播逐条去空白、推荐位去重保序（业务键）")
    void shouldNormalizeAndDedup_whenOfCreated() {
        ShopDecoration decoration = ShopDecoration.of(5L, "  欢迎光临  ",
                List.of(" https://a.png ", "https://b.png"),
                List.of(spuKey(3), spuKey(1), spuKey(3), spuKey(2), spuKey(1)));

        assertEquals("欢迎光临", decoration.getAnnouncement());
        assertEquals(List.of("https://a.png", "https://b.png"), decoration.getBanners());
        assertEquals(List.of(spuKey(3), spuKey(1), spuKey(2)), decoration.getHotSpuIds(),
                "去重且保持首次出现顺序；元素为 SPU 业务键 32-hex");
        assertEquals(5L, decoration.getShopId());
    }

    @Test
    @DisplayName("创建装修 — 未传字段：公告为 null、两个列表为空数组")
    void shouldReturnBlankFields_whenInputNull() {
        ShopDecoration decoration = ShopDecoration.of(5L, null, null, null);

        assertNull(decoration.getAnnouncement());
        assertTrue(decoration.getBanners().isEmpty());
        assertTrue(decoration.getHotSpuIds().isEmpty());
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
    void shouldThrowParamError_whenHotSpusExceedEight() {
        List<String> nineIds = List.of(
                spuKey(1), spuKey(2), spuKey(3), spuKey(4), spuKey(5),
                spuKey(6), spuKey(7), spuKey(8), spuKey(9));

        BizException ex = assertThrows(BizException.class,
                () -> ShopDecoration.of(5L, null, null, nineIds));

        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals("推荐商品位最多 8 个", ex.getDisplayMessage());
    }

    @Test
    @DisplayName("创建装修 — 推荐位形态非法（技术键 / 可读编码）→ 400 PARAM_ERROR fail-fast，不静默丢弃")
    void shouldThrowParamError_whenHotSpuIdNotWireFormat() {
        // 技术键（旧形态）与可读编码（写入端即被拒，契约 §1.8①）都不得通过
        for (String illegal : List.of("7", "prod-001", "0f1e2d3c4b5a69788796a5b4c3d2e1", "zzzz")) {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> ShopDecoration.of(5L, null, null, List.of(illegal)),
                    "非法业务键形态必须 fail-fast: " + illegal);
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        }
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
        assertTrue(blank.getHotSpuIds().isEmpty());
    }
}
