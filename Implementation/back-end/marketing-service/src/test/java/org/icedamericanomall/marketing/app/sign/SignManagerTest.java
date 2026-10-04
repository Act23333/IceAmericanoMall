package org.icedamericanomall.marketing.app.sign;

import org.icedamericanomall.marketing.app.dto.SignResultDTO;
import org.icedamericanomall.marketing.app.points.PointsManager;
import org.icedamericanomall.marketing.domain.port.SignInStore;
import org.icedamericanomall.marketing.domain.service.SignInPointsPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SignManager 编排单元测试 —— 连续签到积分递增（10/15/20 封顶）与重复签到幂等。
 *
 * <p>递增规则用真实的 {@link SignInPointsPolicy} 领域服务（规则本身有独立单测）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignManager 签到编排单元测试")
class SignManagerTest {

    @Mock SignInStore signInStore;
    @Mock PointsManager pointsManager;

    private SignManager signManager;

    @BeforeEach
    void setUp() {
        signManager = new SignManager(signInStore, new SignInPointsPolicy(), pointsManager);
    }

    @Test
    @DisplayName("sign — 连续第1天奖励10分")
    void shouldAward10_whenDay1() {
        when(signInStore.markToday(1L)).thenReturn(true);
        when(signInStore.countContinuous(1L)).thenReturn(1L);

        SignResultDTO vo = signManager.sign(1L);

        assertTrue(vo.isSigned());
        assertEquals(10, vo.getEarnedPoints());
        verify(pointsManager).addPoints(eq(1L), eq(10), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 连续第2天奖励15分")
    void shouldAward15_whenDay2() {
        when(signInStore.markToday(1L)).thenReturn(true);
        when(signInStore.countContinuous(1L)).thenReturn(2L);

        assertEquals(15, signManager.sign(1L).getEarnedPoints());
        verify(pointsManager).addPoints(eq(1L), eq(15), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 连续第3天及以后封顶20分")
    void shouldCapAt20_whenDay3OrMore() {
        when(signInStore.markToday(1L)).thenReturn(true);
        when(signInStore.countContinuous(1L)).thenReturn(7L);

        assertEquals(20, signManager.sign(1L).getEarnedPoints());
        verify(pointsManager).addPoints(eq(1L), eq(20), eq(1), anyString());
    }

    @Test
    @DisplayName("sign — 重复签到不发放积分")
    void shouldAwardNothing_whenAlreadySigned() {
        when(signInStore.markToday(1L)).thenReturn(false);
        when(signInStore.countContinuous(1L)).thenReturn(3L);

        SignResultDTO vo = signManager.sign(1L);

        assertFalse(vo.isSigned());
        assertEquals(0, vo.getEarnedPoints());
        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("status — 仅返回状态不发放积分")
    void shouldReturnStatusWithoutAwarding() {
        when(signInStore.isSignedToday(1L)).thenReturn(true);
        when(signInStore.countContinuous(1L)).thenReturn(4L);
        when(signInStore.countCurrentMonth(1L)).thenReturn(12L);

        SignResultDTO vo = signManager.status(1L);

        assertTrue(vo.isSigned());
        assertEquals(0, vo.getEarnedPoints());
        assertEquals(4L, vo.getContinuousDays());
        assertEquals(12L, vo.getMonthCount());
        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }
}
