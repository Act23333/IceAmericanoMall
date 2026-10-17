package org.icedamericanomall.item.app;

import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.item.adapter.assembler.HomeConfigConverterImpl;
import org.icedamericanomall.item.adapter.request.HomeConfigReq;
import org.icedamericanomall.item.adapter.response.HomeConfigVO;
import org.icedamericanomall.item.domain.model.home.HomeConfig;
import org.icedamericanomall.item.domain.repository.HomeConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 首页装修编排测试（{@code home_config.spu_ids} 口径，ADR-063 §决策 7 表 #3 / 批次 B7b）——
 * 锁定<b>写侧 fail-closed / 读侧 fail-open</b> 的分界：
 *
 * <ul>
 *   <li>写侧：形态非法 / 超 15 项 → 400，<b>不落库</b>；重复项归一后再落库；</li>
 *   <li>读侧：库里含脏项（旧技术键等）时<b>只丢该项</b>，整条配置照常返回（首屏不得被一条坏数据拖垮）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeConfigManager —— spu_ids 写侧 fail-closed / 读侧 fail-open")
class HomeConfigManagerTest {

    private static String key(int i) {
        return String.format("%032x", i);
    }

    @Mock
    private HomeConfigRepository homeConfigRepository;

    private HomeConfigManager manager() {
        return new HomeConfigManager(homeConfigRepository, new HomeConfigConverterImpl());
    }

    private static HomeConfigReq req(String spuIds) {
        HomeConfigReq req = new HomeConfigReq();
        req.setSlot("hot");
        req.setType(1);
        req.setTitle("热销");
        req.setSpuIds(spuIds);
        return req;
    }

    // ==================== 写侧 ====================

    @Test
    @DisplayName("createConfig — 重复项归一后再落库（不落重复值）")
    void shouldDeduplicateOnCreate() {
        when(homeConfigRepository.save(any(HomeConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HomeConfigVO vo = manager().createConfig(req(key(1) + "," + key(2) + "," + key(1)));

        ArgumentCaptor<HomeConfig> captor = ArgumentCaptor.forClass(HomeConfig.class);
        verify(homeConfigRepository).save(captor.capture());
        assertEquals(key(1) + "," + key(2), captor.getValue().getSpuIds());
        assertEquals(key(1) + "," + key(2), vo.getSpuIds());
    }

    @Test
    @DisplayName("createConfig — 任一项形态非法 → 400 且不落库（写错立刻改，别留到首屏）")
    void shouldRejectMalformedOnCreate() {
        assertThrows(BadRequestException.class,
                () -> manager().createConfig(req(key(1) + ",7")));
        verify(homeConfigRepository, never()).save(any(HomeConfig.class));
    }

    @Test
    @DisplayName("updateConfig — 超 15 项（列宽上限）→ 400 且不更新")
    void shouldRejectOverflowOnUpdate() {
        when(homeConfigRepository.findById(1L)).thenReturn(Optional.of(HomeConfig.newConfig(
                "hot", 1, "热销", null, null, null, 0, 1)));
        String sixteen = IntStream.rangeClosed(1, 16).mapToObj(HomeConfigManagerTest::key)
                .collect(Collectors.joining(","));

        assertThrows(BadRequestException.class, () -> manager().updateConfig(1L, req(sixteen)));
        verify(homeConfigRepository, never()).update(any(HomeConfig.class));
    }

    @Test
    @DisplayName("updateConfig — 非商品组类型（spu_ids 为空）→ null 落库，不报错")
    void shouldAllowEmptySpuIds() {
        when(homeConfigRepository.findById(1L)).thenReturn(Optional.of(HomeConfig.newConfig(
                "banner", 2, "广告", null, null, null, 0, 1)));

        HomeConfigVO vo = manager().updateConfig(1L, req(null));

        assertNull(vo.getSpuIds());
        verify(homeConfigRepository).update(any(HomeConfig.class));
    }

    // ==================== 读侧 ====================

    @Test
    @DisplayName("listEnabled — 库里含脏项 → 只丢该项、保留其余（fail-open，不抛错）")
    void shouldFilterDirtyItemsOnRead() {
        when(homeConfigRepository.findEnabled()).thenReturn(List.of(
                HomeConfig.newConfig("hot", 1, "热销", key(1) + ",7," + key(3), null, null, 0, 1)));

        List<HomeConfigVO> vos = manager().listEnabled();

        assertEquals(1, vos.size());
        assertEquals(key(1) + "," + key(3), vos.get(0).getSpuIds());
    }

    @Test
    @DisplayName("listEnabled — 全部脏项 → 该配置商品位降级为 null，其余字段照常返回")
    void shouldDegradeOnlySpuIds_whenAllDirty() {
        when(homeConfigRepository.findEnabled()).thenReturn(List.of(
                HomeConfig.newConfig("hot", 1, "热销", "7,8", null, null, 0, 1)));

        List<HomeConfigVO> vos = manager().listEnabled();

        assertEquals(1, vos.size());
        assertNull(vos.get(0).getSpuIds());
        assertEquals("热销", vos.get(0).getTitle());
    }
}
