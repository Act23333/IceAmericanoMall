package org.icedamericanomall.trade.domain.service;

import java.util.List;

/**
 * 运费计价纯函数（F2 运费模型，V5.6）—— <b>无任何框架依赖</b>（不引 Spring / MyBatis / Feign /
 * Lombok），可在单元测试中直接调用。
 *
 * <h2>Phase 1 计价口径（与 04-Data-Model §十二 12.2 / 05 §4.4.9 契约同步）</h2>
 * <ol>
 *   <li><b>同单同商家</b>：跨店下单已被禁止（{@code CROSS_STORE_FORBIDDEN(3014)}），一单内商品同商家，
 *       运费只计<b>一次</b>；</li>
 *   <li><b>取最高者计一次</b>：取订单内各商品<b>生效模板</b>（绑定模板启用则用绑定模板，
 *       否则回落商家默认模板；两者皆无则不计）中 {@code fee} <b>最高</b>者计一次运费，
 *       不按商品叠加；</li>
 *   <li><b>满额包邮</b>：订单商品总额（{@code totalAmount}，= Σ order_item.sub_total）达到该
 *       生效模板的 {@code freeThreshold}（&gt;0）时运费为 0；{@code freeThreshold=0} 表示
 *       该模板不参与满额包邮；</li>
 *   <li><b>兜底</b>：无任何生效模板（商家无启用模板）→ 运费 0（Phase 1 口径，不阻断下单）；
 *       秒杀订单不调用本函数（其运费恒为 0）。</li>
 * </ol>
 *
 * <p><b>调用方职责</b>：把「每个订单项 → 生效模板」的解析结果（{@link Rule} 列表）传入；
 * 解析需经 {@code /internal/item/freight-template/query}（I43）取启用模板与默认模板标记。
 *
 * <p><b>金额单位</b>：一律为「分」（{@code int}）；本类不做四舍五入（无除法）。
 */
public final class PostageCalculator {

    private PostageCalculator() {
    }

    /**
     * 计价入参 —— 一个「生效模板」的计价要素。
     *
     * @param fee           固定运费（分，≥0）
     * @param freeThreshold 满额包邮阈值（分）；{@code null} 或 0 = 不参与满额包邮
     */
    public record Rule(Integer fee, Integer freeThreshold) {
    }

    /**
     * 计算运费：取各生效规则中 {@code fee} 最高者计一次；命中该规则满额阈值则免邮。
     *
     * @param rules       订单内各订单项的生效规则（可为 null / 空 → 运费 0）
     * @param totalAmount 订单商品总额（分，不含运费）
     * @return 运费（分，≥0）
     */
    public static int calculate(List<Rule> rules, int totalAmount) {
        if (rules == null || rules.isEmpty()) {
            return 0;
        }
        Rule picked = null;
        for (Rule rule : rules) {
            if (rule == null || rule.fee() == null) {
                continue;
            }
            // 严格大于：并列时取先出现者（结果等价——fee 相同则金额相同）
            if (picked == null || rule.fee() > picked.fee()) {
                picked = rule;
            }
        }
        if (picked == null) {
            return 0;
        }
        Integer threshold = picked.freeThreshold();
        if (threshold != null && threshold > 0 && totalAmount >= threshold) {
            return 0;
        }
        return Math.max(picked.fee(), 0);
    }
}
