package org.icedamericanomall.user.infrastructure.adapter;

import org.icedamericanomall.user.app.points.PointsManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 新用户建档奖励适配器单元测试（V5.2）—— 发放参数与"失败不阻断建档"的旁路语义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewUserRewardServiceAdapter 单元测试")
class NewUserRewardServiceAdapterTest {

    @Mock
    private PointsManager pointsManager;

    @InjectMocks
    private NewUserRewardServiceAdapter adapter;

    @Test
    @DisplayName("award — 新用户发放 200 分建档奖励（积分类型沿用历史值 2）")
    void shouldAwardWelcomePoints() {
        adapter.award(100L);

        verify(pointsManager).addPoints(eq(100L), eq(200), eq(2), eq("新用户建档奖励"));
    }

    @Test
    @DisplayName("award — userId 为空：不调用积分账本")
    void shouldSkip_whenUserIdNull() {
        adapter.award(null);

        verify(pointsManager, never()).addPoints(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("award — 积分账本异常：只告警不抛出（不阻断建档/登录主链路）")
    void shouldSwallowFailure() {
        doThrow(new RuntimeException("points table down"))
                .when(pointsManager).addPoints(anyLong(), anyInt(), anyInt(), anyString());

        adapter.award(100L);

        verify(pointsManager).addPoints(eq(100L), eq(200), eq(2), eq("新用户建档奖励"));
    }
}
