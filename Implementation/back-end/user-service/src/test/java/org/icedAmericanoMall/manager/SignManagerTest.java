package org.icedAmericanoMall.manager;

import org.icedAmericanoMall.domain.vo.SignResultVO;
import org.icedAmericanoMall.service.PointsService;
import org.icedAmericanoMall.service.UserSignService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SignManager 单元测试 —— 验证连续签到积分递增（10/15/20 封顶）与重复签到幂等。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignManager 签到编排单元测试")
class SignManagerTest {

    @Mock UserSignService userSignService;
    @Mock PointsService pointsService;
    @InjectMocks SignManager signManager;

    @Test
    @DisplayName("sign — 连续第1天奖励10分")
    void shouldAward10_whenDay1() {
        when(userSignService.sign(1L)).thenReturn(true);
        when(userSignService.countContinuousSign(1L)).thenReturn(1L);

        SignResultVO vo = signManager.sign(1L);

        assertTrue(vo.isSigned());
        assertEquals(10, vo.getEarnedPoints());
        verify(pointsService).addPoints(eq(1L), eq(10), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 连续第2天奖励15分")
    void shouldAward15_whenDay2() {
        when(userSignService.sign(1L)).thenReturn(true);
        when(userSignService.countContinuousSign(1L)).thenReturn(2L);

        assertEquals(15, signManager.sign(1L).getEarnedPoints());
        verify(pointsService).addPoints(eq(1L), eq(15), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 连续第3天及以后封顶20分")
    void shouldCapAt20_whenDay3OrMore() {
        when(userSignService.sign(1L)).thenReturn(true);
        when(userSignService.countContinuousSign(1L)).thenReturn(7L);

        assertEquals(20, signManager.sign(1L).getEarnedPoints());
        verify(pointsService).addPoints(eq(1L), eq(20), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 重复签到不发放积分")
    void shouldAwardNothing_whenAlreadySigned() {
        when(userSignService.sign(1L)).thenReturn(false);
        when(userSignService.countContinuousSign(1L)).thenReturn(3L);

        SignResultVO vo = signManager.sign(1L);

        assertFalse(vo.isSigned());
        assertEquals(0, vo.getEarnedPoints());
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("status — 仅返回状态不发放积分")
    void shouldReturnStatusWithoutAwarding() {
        when(userSignService.isTodaySigned(1L)).thenReturn(true);
        when(userSignService.countContinuousSign(1L)).thenReturn(4L);
        when(userSignService.countCurrentMonthSign(1L)).thenReturn(12L);

        SignResultVO vo = signManager.status(1L);

        assertTrue(vo.isSigned());
        assertEquals(0, vo.getEarnedPoints());
        assertEquals(4L, vo.getContinuousDays());
        assertEquals(12L, vo.getMonthCount());
        verify(pointsService, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }
}
