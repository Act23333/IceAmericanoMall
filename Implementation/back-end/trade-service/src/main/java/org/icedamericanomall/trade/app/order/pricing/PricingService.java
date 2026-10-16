package org.icedamericanomall.trade.app.order.pricing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.trade.app.order.strategy.OrderCreateContext;
import org.icedamericanomall.trade.domain.model.order.Order;
import org.icedamericanomall.trade.domain.service.PricingCalculator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 价格计算中心（F5/F6，ADR-043/044）—— 下单链路<b>唯一</b>的优惠计算归宿：
 * 店铺促销（I44）→ 会员折扣（I45）→ 用户券（收编既有 {@code applyCoupons}），合计经总额守卫封顶。
 *
 * <p>所有 Feign 调用发生在下单管道的 {@code buildOrder} 阶段（{@code OrderCreationManager}
 * 整体无事务），<b>Feign 不入事务</b>（硬约束）。三个优惠源逐项独立计算、均以
 * {@code totalAmount} 原价为基准，纯计算逻辑下沉 {@link PricingCalculator}（零框架依赖）。
 *
 * <p><b>降级语义（ADR-011 三分法）</b>：
 * <ul>
 *   <li>I44 促销查询 <b>fail-closed</b>：查询失败抛 {@code BizException(SYSTEM_BUSY)} 使下单失败
 *       （由 {@code PromotionClientFallback} 承担），本类不捕获——静默空集合等于
 *       「故障伪装成无促销」，用户应得优惠被吞；</li>
 *   <li>I45 会员等级 <b>fail-open</b>：查询失败由 {@code MemberClientFallback} 降级返回
 *       默认 NORMAL（不折扣），方向是用户少得优惠（体验损失、可补偿），下单可用性优先。</li>
 * </ul>
 *
 * <p><b>调用时序与补偿前提（V5.19 修正）</b>：促销（I44 fail-closed）与会员（I45 fail-open）
 * 解析先于券核销 —— 这两步失败时用户券尚未核销，无需补偿。但该保证<b>只覆盖核销之前的步骤</b>：
 * 核销是 marketing 侧独立事务（调用返回即已提交），因此核销之后的<b>任何</b>失败
 * （如同为 fail-closed 的 I43 运费解析）都必须由编排层回收券。核销事实由
 * {@link OrderCreateContext#markCouponConsumed(String)} 在核销返回的那一刻显式置位，
 * 补偿侧据此判定 —— <b>不得</b>用 {@code discountAmount > 0} 之类的派生金额推导
 * （金额可能为 0，也可能尚未回填）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingService {

    private final PromotionClient promotionClient;
    private final MemberClient memberClient;
    private final CouponClient couponClient;

    /**
     * 统一计价入口：解析三优惠源并计算最终 {@code discountAmount}。
     *
     * <p>次序（ADR-043）：促销（I44，fail-closed）→ 会员（I45，fail-open）→ 券（核销 Feign，
     * 语义原样收编自既有 {@code applyCoupons}）→ 总额守卫封顶。
     *
     * @param ctx   下单上下文（sellerId / totalAmount / userId / 券 ID / 商品快照，validate 阶段已填充）
     * @param order 待构建订单（提供 orderNo 作为券核销幂等锚点）
     * @return 最终优惠合计（分，经总额守卫封顶，区间 [0, totalAmount]）
     * @throws BizException I44 促销查询失败（SYSTEM_BUSY，fail-closed 透传，不下单）
     */
    public int calculate(OrderCreateContext ctx, Order order) {
        int total = ctx.getTotalAmount();
        int promotionDiscount = resolvePromotionDiscount(ctx.getSellerId(), total);
        int memberDiscount = resolveMemberDiscount(ctx.getUserId(), total);
        int couponDiscount = applyCoupons(ctx, order, total);
        return PricingCalculator.totalDiscount(total, promotionDiscount, memberDiscount, couponDiscount);
    }

    /** I44 店铺促销解析（fail-closed：异常透传使下单失败）→ 择优减免。 */
    private int resolvePromotionDiscount(Long sellerId, int total) {
        PromotionQueryCO query = new PromotionQueryCO();
        query.setSellerId(sellerId);
        List<PromotionCO> actives = promotionClient.queryActive(query);
        List<PricingCalculator.PromotionRule> rules = toRules(actives);
        int discount = PricingCalculator.bestPromotionDiscount(rules, total);
        if (discount > 0) {
            log.info("促销优惠命中: sellerId={}, total={}, discount={}", sellerId, total, discount);
        }
        return discount;
    }

    /** I44 {@code PromotionCO} → 计价要素（领域层不感知 ia-api 类型）。 */
    private List<PricingCalculator.PromotionRule> toRules(List<PromotionCO> actives) {
        if (actives == null || actives.isEmpty()) {
            return List.of();
        }
        return actives.stream()
                .map(p -> new PricingCalculator.PromotionRule(
                        p.getType(), p.getThreshold(), p.getDiscountValue(), p.getMaxDiscount()))
                .collect(Collectors.toList());
    }

    /** I45 会员等级解析（fail-open：降级 NORMAL 不折扣，由 fallback 承担）→ 会员减免。 */
    private int resolveMemberDiscount(Long userId, int total) {
        MemberLevelCO level = memberClient.getLevel(userId);
        return PricingCalculator.memberDiscount(total, level == null ? null : level.getDiscountRate());
    }

    // ==================== 用户券核销（收编自 NormalCartOrderStrategy.applyCoupons，语义原样） ====================

    /**
     * V4.3: 多券叠加折扣计算（京东标准：逐券抵扣 + 叠加规则校验 + scope 校验）。
     * <b>收编说明（ADR-043）</b>：Feign 语义与调用时点原样保留——核销入参
     * {@code orderAmount = total − 已累计券抵扣}、多券先校验叠加规则、累计抵扣封顶 total。
     */
    private int applyCoupons(OrderCreateContext ctx, Order order, int total) {
        List<Long> couponIds = resolveCouponIds(ctx);
        if (couponIds.isEmpty()) return 0;

        // 多券时校验叠加规则
        if (couponIds.size() > 1) {
            validateStackRules(couponIds);
        }

        String spuIds = buildSpuIds(ctx);
        String categoryIds = buildCategoryIds(ctx);

        int totalDiscount = 0;
        for (Long userCouponId : couponIds) {
            Integer applied = couponClient.useCoupon(
                    ctx.getUserId(), userCouponId, order.getOrderNo(), total - totalDiscount,
                    ctx.getOrderType().getCode(), ctx.getSellerId(), spuIds, categoryIds);
            // ★ 核销成功即置位「副作用已发生」的事实标记（本修复核心）：
            //   useCoupon 正常返回 = marketing 侧独立事务已提交，券此刻已被占用。
            //   与金额无关（降级回调可返回 0、推导金额可能尚未回填）——补偿侧一律依据该标记
            //   决定是否 rollbackByOrderNo。未正常返回（抛异常）时券未提交，故不置位。
            ctx.markCouponConsumed(order.getOrderNo() != null ? order.getOrderNo() : ctx.getOrderNo());
            totalDiscount += (applied != null ? applied : 0);
            if (totalDiscount >= total) {
                totalDiscount = total;
                break;
            }
        }
        return totalDiscount;
    }

    /** V4.3: 单券兼容字段（userCouponId）与多券列表（userCouponIds）统一解析。 */
    private List<Long> resolveCouponIds(OrderCreateContext ctx) {
        if (ctx.getUserCouponIds() != null && !ctx.getUserCouponIds().isEmpty()) {
            return ctx.getUserCouponIds();
        }
        if (ctx.getUserCouponId() != null) {
            return List.of(ctx.getUserCouponId());
        }
        return List.of();
    }

    /** V4.3: 多券叠加规则校验（互斥券不可叠、可叠券需同 group）；查询降级不阻断下单。 */
    private void validateStackRules(List<Long> userCouponIds) {
        List<CouponStackInfoCO> infos = couponClient.batchGet(userCouponIds);
        if (infos == null || infos.isEmpty()) return; // 降级：不阻断下单

        // 互斥券不能与其他券叠加
        long exclusiveCount = infos.stream()
                .filter(s -> s.getStackRule() != null && s.getStackRule() == 1).count();
        if (exclusiveCount > 0 && infos.size() > 1) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "互斥券不能与其他优惠券叠加使用");
        }

        // 可叠加券需同 group
        if (infos.size() > 1) {
            String firstGroup = infos.get(0).getStackGroup();
            boolean allSameGroup = infos.stream()
                    .allMatch(s -> Objects.equals(s.getStackGroup(), firstGroup));
            if (!allSameGroup) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "不同叠加组的券不能一起使用");
            }
        }
    }

    /** 优惠券 scope 校验用商品 ID 串（逗号分隔，去空去重）。 */
    private String buildSpuIds(OrderCreateContext ctx) {
        return ctx.getSnapshots().stream()
                .map(s -> s.getSpuId() != null ? String.valueOf(s.getSpuId()) : "")
                .filter(s -> !s.isEmpty())
                .distinct().collect(Collectors.joining(","));
    }

    /** 优惠券 scope 校验用类目 ID 串（逗号分隔，去空去重）。 */
    private String buildCategoryIds(OrderCreateContext ctx) {
        return ctx.getSnapshots().stream()
                .map(s -> s.getCategoryId() != null ? String.valueOf(s.getCategoryId()) : "")
                .filter(s -> !s.isEmpty())
                .distinct().collect(Collectors.joining(","));
    }
}
