package org.icedamericanomall.user.adapter.web.internal;

import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.user.app.member.MemberLevelManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员域内部接口单元测试 —— 验证 I45/I46 委托应用层 Manager（Controller 不写业务逻辑）。
 */
@DisplayName("会员域内部接口 — I45/I46 委托应用层")
@ExtendWith(MockitoExtension.class)
class InternalMemberControllerTest {

    @Mock
    private MemberLevelManager memberLevelManager;

    @InjectMocks
    private InternalMemberController controller;

    @Test
    @DisplayName("I45 — 控制器委托 MemberLevelManager.getLevel 并透传返回值")
    void shouldDelegateToManager_whenGetLevel() {
        MemberLevelCO expected = new MemberLevelCO("GOLD", 9800, 120);
        when(memberLevelManager.getLevel(5L)).thenReturn(expected);

        MemberLevelCO result = controller.getLevel(5L);

        assertSame(expected, result);
        verify(memberLevelManager).getLevel(5L);
    }

    @Test
    @DisplayName("I46 — 控制器委托 MemberLevelManager.recordGrowth 并透传返回值")
    void shouldDelegateToManager_whenRecordGrowth() {
        MemberGrowthCO growth = new MemberGrowthCO(5L, "O20261004001", 50000);
        MemberLevelCO expected = new MemberLevelCO("SILVER", 9900, 110);
        when(memberLevelManager.recordGrowth(growth)).thenReturn(expected);

        MemberLevelCO result = controller.recordGrowth(growth);

        assertSame(expected, result);
        verify(memberLevelManager).recordGrowth(growth);
    }
}
