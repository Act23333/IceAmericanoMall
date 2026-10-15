package org.icedamericanomall.user.app.favorite;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.user.adapter.response.FavoriteVO;
import org.icedamericanomall.user.app.dto.ProductBriefView;
import org.icedamericanomall.user.app.port.FavoriteProductBriefQuery;
import org.icedamericanomall.user.domain.model.favorite.Favorite;
import org.icedamericanomall.user.domain.repository.FavoriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收藏列表商品字段回填单元测试（契约 §4.2.6 U20 {@code FavoriteVO}）。
 *
 * <p>覆盖两类必测面：①商品摘要存在时四个字段被正确填充（且<b>批量一次</b>查询）；
 * ②摘要缺失 / 数据源降级时字段置空但接口不抛异常（fail-open）。
 */
@DisplayName("收藏列表 — 商品字段回填（契约 U20 FavoriteVO）")
@ExtendWith(MockitoExtension.class)
class FavoriteManagerTest {

    private static final Long USER_ID = 100L;
    private static final Long PRODUCT_ID = 1001L;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private FavoriteProductBriefQuery favoriteProductBriefQuery;

    @InjectMocks
    private FavoriteManager favoriteManager;

    @Test
    @DisplayName("分页查询 — 商品摘要在 → product_name/main_image/price/status 被正确填充")
    void shouldFillProductFields_whenBriefAvailable() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(favoriteRepository.findPageDescByUserId(USER_ID, 1, 20)).thenReturn(List.of(favorite(7L, PRODUCT_ID)));
        when(favoriteProductBriefQuery.findByIds(List.of(PRODUCT_ID)))
                .thenReturn(Map.of(PRODUCT_ID, new ProductBriefView(PRODUCT_ID, "手工陶瓷茶杯",
                        "https://cdn.icedmall.com/product/main_1001.jpg", 12800, 1)));

        IPage<FavoriteVO> page = favoriteManager.pageByUser(USER_ID, 1, 20);

        assertEquals(1, page.getRecords().size());
        FavoriteVO vo = page.getRecords().get(0);
        assertEquals(7L, vo.getId());
        assertEquals(PRODUCT_ID, vo.getProductId());
        assertEquals("手工陶瓷茶杯", vo.getProductName());
        assertEquals("https://cdn.icedmall.com/product/main_1001.jpg", vo.getMainImage());
        assertEquals(12800, vo.getPrice());
        assertEquals(1, vo.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), vo.getCreateTime());
    }

    @Test
    @DisplayName("分页查询 — 多行收藏只打一次商品摘要查询（无 N+1）")
    void shouldQueryBriefOnce_whenPageHasMultipleRows() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(3L);
        when(favoriteRepository.findPageDescByUserId(USER_ID, 1, 20)).thenReturn(List.of(
                favorite(1L, 1001L), favorite(2L, 1002L), favorite(3L, 1001L)));
        when(favoriteProductBriefQuery.findByIds(any())).thenReturn(Map.of());

        favoriteManager.pageByUser(USER_ID, 1, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(favoriteProductBriefQuery).findByIds(captor.capture());
        assertEquals(List.of(1001L, 1002L), List.copyOf(captor.getValue()), "ID 应去重且保持出现顺序");
    }

    @Test
    @DisplayName("分页查询 — 商品摘要缺失（已删除商品）→ 商品字段为 null 且不抛异常")
    void shouldLeaveProductFieldsNull_whenBriefMissing() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(favoriteRepository.findPageDescByUserId(USER_ID, 1, 20)).thenReturn(List.of(favorite(7L, PRODUCT_ID)));
        when(favoriteProductBriefQuery.findByIds(List.of(PRODUCT_ID))).thenReturn(Map.of());

        IPage<FavoriteVO> page = favoriteManager.pageByUser(USER_ID, 1, 20);

        FavoriteVO vo = page.getRecords().get(0);
        assertEquals(PRODUCT_ID, vo.getProductId(), "收藏本体字段不受影响");
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), vo.getCreateTime());
        assertNull(vo.getProductName());
        assertNull(vo.getMainImage());
        assertNull(vo.getPrice());
        assertNull(vo.getStatus());
    }

    @Test
    @DisplayName("分页查询 — 商品摘要数据源不可用 → 降级返回空字段，主流程不失败（fail-open）")
    void shouldDegradeGracefully_whenBriefQueryThrows() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(favoriteRepository.findPageDescByUserId(USER_ID, 1, 20)).thenReturn(List.of(favorite(7L, PRODUCT_ID)));
        when(favoriteProductBriefQuery.findByIds(List.of(PRODUCT_ID)))
                .thenThrow(new IllegalStateException("item 域不可用"));

        IPage<FavoriteVO> page = favoriteManager.pageByUser(USER_ID, 1, 20);

        assertEquals(1, page.getRecords().size(), "列表本身必须照常返回");
        assertNull(page.getRecords().get(0).getProductName());
    }

    @Test
    @DisplayName("分页查询 — 本人无收藏 → 不触发商品摘要查询")
    void shouldSkipBriefQuery_whenNoFavorites() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(0L);

        IPage<FavoriteVO> page = favoriteManager.pageByUser(USER_ID, 1, 20);

        assertTrue(page.getRecords().isEmpty());
        assertEquals(0L, page.getTotal());
        verify(favoriteProductBriefQuery, never()).findByIds(any());
    }

    @Test
    @DisplayName("分页查询 — 商品摘要端口返回 null → 按缺失处理，不抛 NPE")
    void shouldTolerateNullBriefMap_whenPortReturnsNull() {
        when(favoriteRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(favoriteRepository.findPageDescByUserId(USER_ID, 1, 20)).thenReturn(List.of(favorite(7L, PRODUCT_ID)));
        when(favoriteProductBriefQuery.findByIds(List.of(PRODUCT_ID))).thenReturn(null);

        IPage<FavoriteVO> page = favoriteManager.pageByUser(USER_ID, 1, 20);

        assertNotNull(page.getRecords().get(0));
        assertNull(page.getRecords().get(0).getProductName());
    }

    private Favorite favorite(Long id, Long productId) {
        return Favorite.builder()
                .id(id)
                .userId(USER_ID)
                .productId(productId)
                .createTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build();
    }
}
