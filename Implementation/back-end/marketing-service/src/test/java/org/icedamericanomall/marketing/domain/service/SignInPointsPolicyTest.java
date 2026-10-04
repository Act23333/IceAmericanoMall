package org.icedamericanomall.marketing.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 签到积分策略纯单元测试 —— 连续签到奖励递增（10/15/20 封顶）。
 */
@DisplayName("SignInPointsPolicy 领域单元测试")
class SignInPointsPolicyTest {

    private final SignInPointsPolicy policy = new SignInPointsPolicy();

    @Test
    @DisplayName("连续第1天：10 分")
    void shouldAward10_whenDay1() {
        assertEquals(10, policy.earnedPoints(1));
    }

    @Test
    @DisplayName("连续第2天：15 分")
    void shouldAward15_whenDay2() {
        assertEquals(15, policy.earnedPoints(2));
    }

    @Test
    @DisplayName("连续第3天及以后：封顶 20 分")
    void shouldCapAt20_whenDay3OrMore() {
        assertEquals(20, policy.earnedPoints(3));
        assertEquals(20, policy.earnedPoints(7));
        assertEquals(20, policy.earnedPoints(100));
    }
}
