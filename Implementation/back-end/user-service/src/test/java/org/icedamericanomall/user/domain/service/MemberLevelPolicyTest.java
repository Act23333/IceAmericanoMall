package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberLevelRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会员等级重算纯函数单元测试（无框架依赖，直接构造规则集调用 —— ADR-044 重算口径）。
 *
 * <p>规则集与 V5.8 seed 对齐：NORMAL(0)/SILVER(100000)/GOLD(500000)/PLATINUM(2000000)，分。
 */
@DisplayName("会员等级重算纯函数 — 门槛匹配 + 只升不降")
class MemberLevelPolicyTest {

    /** V5.8 seed 规则集（threshold 分；NORMAL 0 / SILVER 1000 元 / GOLD 5000 元 / PLATINUM 20000 元）。 */
    private static List<MemberLevelRule> seedRules() {
        return List.of(
                new MemberLevelRule(MemberLevelEnum.NORMAL, 0, 10000, 100, 0),
                new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1),
                new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2),
                new MemberLevelRule(MemberLevelEnum.PLATINUM, 2000000, 9500, 150, 3));
    }

    @Test
    @DisplayName("当前等级高于门槛匹配等级 — 保持 GOLD 不降级")
    void shouldStayAtGold_whenCurrentLevelHigherThanMatched() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                MemberLevelEnum.GOLD, 120000, seedRules());

        assertEquals(MemberLevelEnum.GOLD, target);
    }

    @Test
    @DisplayName("累计成长值跨过 GOLD 门槛 — 升级为 GOLD")
    void shouldUpgradeToGold_whenAccumulatedReachesGoldThreshold() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                MemberLevelEnum.SILVER, 500000, seedRules());

        assertEquals(MemberLevelEnum.GOLD, target);
    }

    @Test
    @DisplayName("边界 = 门槛即达成 — 累计值恰等于 SILVER 门槛时匹配 SILVER")
    void shouldMatchSilver_whenAccumulatedEqualsThreshold() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                MemberLevelEnum.NORMAL, 100000, seedRules());

        assertEquals(MemberLevelEnum.SILVER, target);
    }

    @Test
    @DisplayName("累计值低于全部门槛（规则集无 NORMAL 行）— 维持 NORMAL")
    void shouldStayNormal_whenAccumulatedBelowAllThresholds() {
        List<MemberLevelRule> rules = List.of(
                new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1),
                new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2));

        MemberLevelEnum target = MemberLevelPolicy.recalculate(MemberLevelEnum.NORMAL, 99999, rules);

        assertEquals(MemberLevelEnum.NORMAL, target);
    }

    @Test
    @DisplayName("规则集为空 — 视为无门槛，维持当前等级")
    void shouldKeepCurrent_whenRulesEmpty() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                MemberLevelEnum.GOLD, 999999, List.of());

        assertEquals(MemberLevelEnum.GOLD, target);
    }

    @Test
    @DisplayName("当前等级规则缺行 — 按枚举声明序兜底比较，PLATINUM 不被降级")
    void shouldKeepPlatinum_whenCurrentRuleMissing() {
        List<MemberLevelRule> rules = List.of(
                new MemberLevelRule(MemberLevelEnum.SILVER, 100000, 9900, 110, 1),
                new MemberLevelRule(MemberLevelEnum.GOLD, 500000, 9800, 120, 2));

        MemberLevelEnum target = MemberLevelPolicy.recalculate(MemberLevelEnum.PLATINUM, 1000000, rules);

        assertEquals(MemberLevelEnum.PLATINUM, target);
    }

    @Test
    @DisplayName("当前等级缺省（null）— 按 NORMAL 处理并正常升级")
    void shouldUpgrade_whenCurrentNullAndThresholdReached() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(null, 600000, seedRules());

        assertEquals(MemberLevelEnum.GOLD, target);
    }

    @Test
    @DisplayName("零成长值 — 匹配 NORMAL（门槛 0），维持 NORMAL")
    void shouldStayNormal_whenZeroGrowth() {
        MemberLevelEnum target = MemberLevelPolicy.recalculate(
                MemberLevelEnum.NORMAL, 0, seedRules());

        assertEquals(MemberLevelEnum.NORMAL, target);
    }
}
