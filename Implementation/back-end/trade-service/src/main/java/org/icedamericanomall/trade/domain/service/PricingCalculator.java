package org.icedamericanomall.trade.domain.service;

import java.util.List;

/**
 * 价格计算纯函数（F5 价格计算中心，ADR-043）—— <b>无任何框架依赖</b>（不引 Spring / MyBatis /
 * Feign / Lombok），可在单元测试中直接调用（对齐 {@link PostageCalculator} 先例）。
 *
 * <h2>计价口径（ADR-043，逐条锁定）</h2>
 * <ol>
 *   <li><b>三源可叠加</b>：店铺促销（I44）→ 会员折扣（I45）→ 用户券，三者<b>逐项独立计算</b>，
 *       均以 {@code totalAmount}（= Σ order_item.sub_total 原价）为基准，不逐级递减；</li>
 *   <li><b>同类型促销取减免金额最大者</b>：同店铺命中多个生效活动不叠加——在
 *       {@code totalAmount ≥ threshold} 的候选中逐个算减免、取<b>减免最大者</b>（与券叠加是两回事）；</li>
 *   <li><b>满减</b>（type=1）：减免 = {@code discountValue}（分）；</li>
 *   <li><b>满折</b>（type=2，discountValue 为万分比 8500=8.5折）：减免 =
 *       {@code min(totalAmount × (10000 − discountValue) / 10000, maxDiscount > 0 ? maxDiscount : ∞)}
 *       （整数除法向下取整）；</li>
 *   <li><b>会员折扣</b>：减免 = {@code totalAmount × (10000 − discountRate) / 10000}
 *       （整数除法向下取整；discountRate=10000 即不折扣）；</li>
 *   <li><b>总额守卫</b>：三项合计 &gt; {@code totalAmount} 时封顶为 {@code totalAmount}
 *       —— {@code payAmount = totalAmount − discountAmount + postFee} 恒 ≥ 0。</li>
 * </ol>
 *
 * <p><b>秒杀单不进本计算器</b>：秒杀价已是深度优惠（快照价直改），维持
 * {@code discountAmount = 0}（{@code FlashSaleOrderStrategy} 不调用计价中心）。
 *
 * <p><b>调用方职责</b>：把 I44 查询结果转换为 {@link PromotionRule} 列表传入（本类不感知
 * Feign / CO 类型）；解析与编排见 app 层 {@code PricingService}。
 *
 * <p><b>金额单位</b>：一律为「分」（{@code int}）；万分比/百分比的除法均为整数除法（向下取整）。
 */
public final class PricingCalculator {

    /** 促销类型：满减（FIXED）—— discountValue 即减免金额（分）。 */
    public static final int PROMO_TYPE_FIXED = 1;

    /** 促销类型：满折（PERCENTAGE）—— discountValue 为万分比（8500=8.5折）。 */
    public static final int PROMO_TYPE_PERCENTAGE = 2;

    /** 万分比基准（discountValue / discountRate 的分母），8500 = 8.5折 = 减 15%。 */
    private static final int RATE_DENOMINATOR = 10000;

    private PricingCalculator() {
    }

    /**
     * 促销计价要素（由调用方从 I44 {@code PromotionCO} 转换而来，本类不依赖 ia-api 类型）。
     *
     * @param type          1=满减(FIXED) / 2=满折(PERCENTAGE)；null 或未知类型该条不参与计价
     * @param threshold     门槛：{@code totalAmount ≥ threshold} 生效（分）；null 视为 0（恒生效）
     * @param discountValue 满减=减免金额（分）；满折=折扣率万分比（8500=8.5折）
     * @param maxDiscount   满折封顶减免额（分）；null 或 0 = 不封顶
     */
    public record PromotionRule(Integer type, Integer threshold, Integer discountValue,
                                Integer maxDiscount) {
    }

    /**
     * 促销择优：在门槛命中的候选活动中逐个计算减免，取<b>减免金额最大者</b>（不叠加）。
     *
     * @param rules       该店铺进行中的促销活动（可为 null / 空 → 0）
     * @param totalAmount 订单商品总额（分）
     * @return 促销减免金额（分，≥0）
     */
    public static int bestPromotionDiscount(List<PromotionRule> rules, int totalAmount) {
        if (rules == null || rules.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (PromotionRule rule : rules) {
            int reduction = promotionReduction(rule, totalAmount);
            if (reduction > best) {
                best = reduction;
            }
        }
        return best;
    }

    /**
     * 单条促销的减免金额：未达门槛或类型未知 → 0；满减取 discountValue；满折按万分比
     * 整数除法计算并按 {@code maxDiscount} 封顶。
     *
     * @param rule        促销计价要素（可为 null → 0）
     * @param totalAmount 订单商品总额（分）
     * @return 该活动减免金额（分，≥0）
     */
    static int promotionReduction(PromotionRule rule, int totalAmount) {
        if (rule == null || rule.type() == null || rule.discountValue() == null) {
            return 0;
        }
        Integer threshold = rule.threshold();
        if (threshold != null && totalAmount < threshold) {
            return 0;   // 门槛未命中（≥ 生效，= 也生效）
        }
        int reduction;
        if (rule.type() == PROMO_TYPE_FIXED) {
            reduction = rule.discountValue();
        } else if (rule.type() == PROMO_TYPE_PERCENTAGE) {
            reduction = percentageReduction(totalAmount, rule.discountValue(), rule.maxDiscount());
        } else {
            return 0;   // 未知类型不参与计价
        }
        return Math.max(reduction, 0);
    }

    /**
     * 满折减免：{@code totalAmount × (10000 − discountValue) / 10000}（整数除法向下取整），
     * 再按 {@code maxDiscount > 0} 时封顶。discountValue ≥ 10000 视为无优惠（减免 0）。
     */
    private static int percentageReduction(int totalAmount, int discountValue, Integer maxDiscount) {
        if (discountValue >= RATE_DENOMINATOR) {
            return 0;
        }
        int reduction = totalAmount * (RATE_DENOMINATOR - discountValue) / RATE_DENOMINATOR;
        if (maxDiscount != null && maxDiscount > 0) {
            reduction = Math.min(reduction, maxDiscount);
        }
        return Math.max(reduction, 0);
    }

    /**
     * 会员折扣减免：{@code totalAmount × (10000 − discountRate) / 10000}（整数除法向下取整）。
     *
     * @param totalAmount  订单商品总额（分）
     * @param discountRate 会员折扣率万分比（10000=不折扣，9800=98折）；null 视为不折扣
     * @return 会员减免金额（分，区间 [0, totalAmount]）
     */
    public static int memberDiscount(int totalAmount, Integer discountRate) {
        if (discountRate == null || discountRate >= RATE_DENOMINATOR) {
            return 0;
        }
        int reduction = totalAmount * (RATE_DENOMINATOR - discountRate) / RATE_DENOMINATOR;
        return Math.max(0, Math.min(reduction, totalAmount));
    }

    /**
     * 合计守卫封顶：促销 + 会员 + 券三项合计超过 {@code totalAmount} 时以
     * {@code totalAmount} 封顶（payAmount 恒 ≥ 0，ADR-043 总额守卫）。
     *
     * @param totalAmount      订单商品总额（分）
     * @param promotionDiscount 促销减免（分）
     * @param memberDiscount    会员减免（分）
     * @param couponDiscount    用户券减免（分）
     * @return 最终 discountAmount（分，区间 [0, totalAmount]）
     */
    public static int totalDiscount(int totalAmount, int promotionDiscount,
                                    int memberDiscount, int couponDiscount) {
        if (totalAmount <= 0) {
            return 0;
        }
        int sum = Math.max(promotionDiscount, 0) + Math.max(memberDiscount, 0)
                + Math.max(couponDiscount, 0);
        return Math.min(sum, totalAmount);
    }
}
