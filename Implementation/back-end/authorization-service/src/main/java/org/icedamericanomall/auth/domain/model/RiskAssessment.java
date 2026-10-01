package org.icedamericanomall.auth.domain.model;

import org.icedamericanomall.auth.domain.enums.RiskLevel;
import org.icedamericanomall.auth.domain.enums.RiskTag;

import java.util.Map;
import java.util.Set;

/**
 * 风险评估结果 —— 风控的完整输出（不可变值对象）。
 * <p>包含四个维度：
 * <ul>
 *   <li>{@link #level}：总体严重程度（用于快速判断）</li>
 *   <li>{@link #tags}：命中的具体异常（用于精细化决策）</li>
 *   <li>{@link #details}：原始维度数据（用于调试、审计）</li>
 *   <li>{@link #captchaVerified}：<b>本次请求是否已通过人机验证</b>。
 *       由应用层在评估后置入（{@link #withCaptchaVerified(boolean)}），
 *       供 {@code CaptchaHandler} 判断"是否还需要要求验证" ——
 *       缺少该标记会导致"前端带着验证凭证重试仍被要求验证"的<b>死循环</b>，
 *       R3 闭环无法成立</li>
 * </ul>
 */
public record RiskAssessment(
        RiskLevel level,
        Set<RiskTag> tags,
        Map<String, Object> details,
        boolean captchaVerified
) {

    public RiskAssessment {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** 便捷构造：默认"未通过人机验证"（风控评估阶段只产出风险维度，验证结果由应用层补入） */
    public RiskAssessment(RiskLevel level, Set<RiskTag> tags, Map<String, Object> details) {
        this(level, tags, details, false);
    }

    /**
     * 标记本次请求的人机验证结果（不可变对象 → 返回副本）。
     *
     * @param verified true = 本次请求已通过人机验证
     * @return 标记后的评估结果（值未变时返回自身）
     */
    public RiskAssessment withCaptchaVerified(boolean verified) {
        return this.captchaVerified == verified
                ? this
                : new RiskAssessment(level, tags, details, verified);
    }

    /** 判断是否命中某个标签 */
    public boolean hasTag(RiskTag tag) {
        return tags.contains(tag);
    }

    /** 判断等级是否达到阈值 */
    public boolean isAtLeast(RiskLevel threshold) {
        return level.isAtLeast(threshold);
    }

    /** 空评估（默认低风险） */
    public static RiskAssessment low() {
        return new RiskAssessment(RiskLevel.LOW, Set.of(), Map.of());
    }
}
