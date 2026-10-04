package org.icedamericanomall.user.infrastructure.persistence.favorite;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedamericanomall.user.domain.model.favorite.Favorite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 收藏仓储实现单元测试 —— 去重写入与 PO ↔ 领域对象的端口往返。
 *
 * <p>仅用标准 {@code BaseMapper} 方法（非 lambdaQuery 链），可用 Mockito 替身验证。
 */
@DisplayName("收藏仓储 — 端口往返与去重")
@ExtendWith(MockitoExtension.class)
class FavoriteRepositoryImplTest {

    @Mock
    private FavoriteMapper favoriteMapper;

    @InjectMocks
    private FavoriteRepositoryImpl repository;

    @Test
    @DisplayName("addIfAbsent — 不存在则插入，业务键落入 PO")
    void shouldInsert_whenAbsent() {
        when(favoriteMapper.selectCount(any())).thenReturn(0L);

        repository.addIfAbsent(Favorite.of(100L, 200L));

        ArgumentCaptor<FavoritePO> captor = ArgumentCaptor.forClass(FavoritePO.class);
        verify(favoriteMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getUserId());
        assertEquals(200L, captor.getValue().getProductId());
    }

    @Test
    @DisplayName("addIfAbsent — 已存在则跳过插入（去重语义）")
    void shouldSkipInsert_whenPresent() {
        when(favoriteMapper.selectCount(any())).thenReturn(1L);

        repository.addIfAbsent(Favorite.of(100L, 200L));

        verify(favoriteMapper, never()).insert(any(FavoritePO.class));
    }

    @Test
    @DisplayName("removeByUserIdAndProductId — 按业务键删除并返回影响行数")
    void shouldDelete_byBusinessKey() {
        when(favoriteMapper.delete(any())).thenReturn(1);

        assertEquals(1, repository.removeByUserIdAndProductId(100L, 200L));
        verify(favoriteMapper).delete(any());
    }

    @Test
    @DisplayName("findPageDescByUserId — PO 记录映射回领域对象")
    void shouldMapPage_toDomain() {
        FavoritePO po = new FavoritePO();
        po.setId(7L);
        po.setUserId(100L);
        po.setProductId(200L);
        po.setCreateTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        Page<FavoritePO> page = new Page<>(1, 20);
        page.setRecords(List.of(po));
        when(favoriteMapper.selectPage(any(), any())).thenReturn(page);

        List<Favorite> records = repository.findPageDescByUserId(100L, 1, 20);

        assertEquals(1, records.size());
        assertEquals(7L, records.get(0).getId());
        assertEquals(100L, records.get(0).getUserId());
        assertEquals(200L, records.get(0).getProductId());
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), records.get(0).getCreateTime());
    }
}
