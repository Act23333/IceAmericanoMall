package org.icedamericanomall.user.domain.service;

import org.icedamericanomall.user.domain.enums.MemberLevelEnum;
import org.icedamericanomall.user.domain.model.member.MemberLevelRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 会员等级重算纯函数（F6 会员等级，ADR-044）—— <b>无任何框架依赖</b>（不引 Spring / MyBatis /
 * Feign / Lombok），单元测试中直接构造入参调用。
 *
 * <h2>重算口径（Phase 1 只升不降）</h2>
 * <ol>
 *   <li><b>门槛匹配</b>：取「累计成长值 ≥ threshold」的规则中门槛最高者为其匹配等级
 *       （门槛并列按 sort 升序取先者，结果等价）；无任何匹配（累计值低于最低门槛）→ NORMAL；</li>
 *   <li><b>只升不降</b>：目标等级 = max(当前等级, 门槛匹配等级) —— 成长值按单累计、永不扣减，
 *       年度降级考核（周期快照/清零口径）留 roadmap；</li>
 *   <li><b>比较基准</b>：等级序以 {@link MemberLevelRule#threshold()}（累计成长值门槛，分）为准 ——
 *       与 {@code sort}（升序）在 seed 数据中同序等价，选 threshold 因匹配/升级判断本身以
 *       门槛为语义（选型说明见 {@link MemberLevelRule}）；规则缺行（如 PLATINUM 行被删）导致
 *       门槛不可比时，以枚举声明序（{@code ordinal()}，NORMAL &lt; SILVER &lt; GOLD &lt; PLATINUM，
 *       与 seed sort 同序）兜底，保证比较总能完成。</li>
 * </ol>
 */
public final class MemberLevelPolicy {

    private MemberLevelPolicy() {
    }

    /**
     * 等级重算：目标等级 = max(当前等级, 门槛匹配等级)（只升不降）。
     *
     * @param currentLevel      当前等级（null 按 NORMAL 处理 —— 存量行缺省口径）
     * @param accumulatedGrowth 入账后累计成长值（分，≥ 0）
     * @param rules             等级规则集（可为 null/空 —— 视为无门槛，维持当前等级）
     * @return 重算后的目标等级（非 null）
     */
    public static MemberLevelEnum recalculate(MemberLevelEnum currentLevel, long accumulatedGrowth,
                                              List<MemberLevelRule> rules) {
        MemberLevelEnum current = currentLevel == null ? MemberLevelEnum.NORMAL : currentLevel;
        MemberLevelEnum matched = matchLevel(accumulatedGrowth, rules);
        return isHigher(matched, current, rules) ? matched : current;
    }

    /**
     * 门槛匹配：取「累计成长值 ≥ threshold」中门槛最高者。
     *
     * <p>防御性重排（threshold 升序、并列按 sort 升序），不依赖仓储返回顺序。
     *
     * @param accumulatedGrowth 累计成长值（分）
     * @param rules             等级规则集（可为 null/空）
     * @return 匹配等级；无任何规则门槛被达成时返回 NORMAL
     */
    private static MemberLevelEnum matchLevel(long accumulatedGrowth, List<MemberLevelRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return MemberLevelEnum.NORMAL;
        }
        MemberLevelRule matched = null;
        for (MemberLevelRule rule : sortedRules(rules)) {
            if (accumulatedGrowth >= rule.threshold()) {
                matched = rule;
            }
        }
        return matched == null ? MemberLevelEnum.NORMAL : matched.level();
    }

    /**
     * candidate 是否严格高于 current（严格高于才升级；等序保持现状，永不降级）。
     *
     * <p>比较基准见类注释第 3 点：双方规则齐备按 threshold，缺行走枚举声明序兜底。
     */
    private static boolean isHigher(MemberLevelEnum candidate, MemberLevelEnum current, List<MemberLevelRule> rules) {
        if (candidate == current) {
            return false;
        }
        Integer candidateRank = rankOf(candidate, rules);
        Integer currentRank = rankOf(current, rules);
        if (candidateRank != null && currentRank != null) {
            return candidateRank > currentRank;
        }
        return candidate.ordinal() > current.ordinal();
    }

    /** 等级序（门槛，分）；该等级规则缺行返回 null（由调用方走枚举声明序兜底）。 */
    private static Integer rankOf(MemberLevelEnum level, List<MemberLevelRule> rules) {
        if (rules == null) {
            return null;
        }
        return rules.stream()
                .filter(rule -> rule.level() == level)
                .map(MemberLevelRule::threshold)
                .findFirst()
                .orElse(null);
    }

    /** 防御性排序：threshold 升序，并列按 sort 升序（seed 数据两者同序，结果等价）。 */
    private static List<MemberLevelRule> sortedRules(List<MemberLevelRule> rules) {
        List<MemberLevelRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(MemberLevelRule::threshold).thenComparingInt(MemberLevelRule::sort));
        return sorted;
    }
}
