package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.MemberClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.dto.MemberGrowthCO;
import org.icedamericanomall.dto.MemberLevelCO;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 会员等级/成长值降级工厂 —— <b>fail-open（返回「未知」，不伪造等级）</b>，
 * 属 ADR-011 的 <b>① 非判别型辅助查询 / best-effort 命令</b>。
 *
 * <p><b>为什么不是 fail-closed（与本包大部分写/定价面不同）</b>：ADR-044 明确裁决会员折扣为
 * 「下单可用性优先」，方向是用户<b>少得优惠</b>（体验损失、可补偿），而不是错价或超卖。
 * 更关键的是<b>调用点位置</b>：{@code trade OrderLifecycleManager#awardOrderRewardPoints}
 * 在「订单已置确认收货」<b>之后、try/catch 之外</b>调用 {@code getLevel} —— 若此处抛错，
 * 一次已完成的收货会被翻成接口报错。故本方法不得抛异常（<b>4xx 也不透传</b>），
 * 这是本包唯一显式豁免 §三·八·1 透传的失败面，理由就是上面这条调用时序。</p>
 *
 * <p><b>返回值口径（本批修正：不再伪造 NORMAL）</b>：
 * <ul>
 *   <li>{@code getLevel} 降级返回 <b>{@code null}（未知）</b>，而不是
 *       {@code new MemberLevelCO("NORMAL", 10000, 100)}。旧写法让「拿不到」与
 *       「用户就是 NORMAL」在出参上<b>无法区分</b>（§三·八·2 要求可区分）。
 *       两个调用方都已 null-safe 且语义等价：
 *       {@code PricingCalculator#memberDiscount(total, null)} = 0 减免、
 *       {@code OrderLifecycleManager#resolvePointsRate(null)} = 1 倍积分。</li>
 *   <li>{@code recordGrowth} 降级返回 {@code null} 并告警：成长值入账失败不阻断交易主流程
 *       （与触达接线 ADR-042 同口径），Phase 1 不做补账任务。</li>
 * </ul>
 */
@Slf4j
@Component
public class MemberClientFallback implements FallbackFactory<MemberClient> {

    @Override
    public MemberClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        // 本工厂两个方法都不抛（见类注释「调用点位置」），category 只用于日志分类与告警定级。
        ErrorCategory category = UpstreamFailures.classify(cause);
        return new MemberClient() {

            @Override
            public MemberLevelCO getLevel(Long userId) {
                log.warn("会员等级查询降级：暂时无法获取，返回 null（=未知，调用方按不折扣/1 倍积分处理）"
                        + " userId={}, category={}", userId, category, cause);
                return null;
            }

            @Override
            public MemberLevelCO recordGrowth(MemberGrowthCO growth) {
                log.warn("会员成长值入账降级：暂时无法入账（本单成长值未记录，Phase 1 不补账）"
                                + " userId={}, orderNo={}, payAmount={}, category={}",
                        growth == null ? null : growth.getUserId(),
                        growth == null ? null : growth.getOrderNo(),
                        growth == null ? null : growth.getPayAmount(), category, cause);
                return null;
            }
        };
    }
}
