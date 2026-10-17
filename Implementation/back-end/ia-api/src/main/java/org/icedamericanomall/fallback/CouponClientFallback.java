package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CouponClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优惠券写面降级工厂 —— <b>三个方法一律 fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p><b>为什么不能 fail-open（依据读调用方代码得出）</b>：
 * <ul>
 *   <li>{@code useCoupon} —— <b>定价输入 + 核销写</b>。调用方
 *       {@code trade-service PricingService#applyCoupons} 在<b>正常返回的那一刻</b>就置位
 *       「券已核销」事实标记（{@code ctx.markCouponConsumed}）并累加抵扣额。旧实现降级返回
 *       {@code 0}，于是出现<b>两个同时成立的错误</b>：①用户以为用了券、实际按原价付款
 *       （券没被核销、也没抵扣，用户多付）；②编排层却把「核销已发生」记成事实，
 *       后续失败时去回滚一张从未核销的券。故 0 这个"成功"值是纯谎报，必须抛错让下单失败。</li>
 *   <li>{@code rollbackByOrderNo} —— <b>对账回收命令</b>。调用方
 *       {@code CouponReconcileManager#reconcile} 在 {@code try} 里「回滚 → 清除处理中标记 →
 *       记 WARN 已回收」，异常才保留下轮重试。旧实现静默返回 = 回滚没发生但标记被清掉、
 *       还打出「已回收」日志 ⇒ <b>用户券被永久吞掉（资损）</b>。</li>
 *   <li>{@code batchGet} —— <b>券叠加规则（定价输入）</b>。调用方
 *       {@code PricingService#validateStackRules} 以「空 ⇒ 不校验」处理多券叠加；
 *       旧实现降级返回空列表 = 跳过互斥券/叠加组校验，互斥券可能被一起使用（平台多让利，
 *       与促销口径不一致）。属「降级成空值会让优惠算错」，按判据 fail-closed。</li>
 * </ul>
 *
 * <p>分支：{@code category == CLIENT}（券不可用/已使用/超出范围等<b>业务事实</b>）→ 原样抛
 * {@link UpstreamException}（保留下游 code/文案/traceId）；{@code TRANSIENT} / {@code BUG} →
 * 抛 {@code BizException(SYSTEM_BUSY)}，文案为「暂时无法…」（§三·八·2）。
 */
@Slf4j
@Component
public class CouponClientFallback implements FallbackFactory<CouponClient> {

    /** TRANSIENT/BUG 路径的降级文案（核销）。 */
    private static final String USE_DEGRADED_MESSAGE = "优惠券暂时无法使用，请稍后重试";
    /** TRANSIENT/BUG 路径的降级文案（对账回滚）。 */
    private static final String ROLLBACK_DEGRADED_MESSAGE = "优惠券暂时无法回滚，请稍后重试";
    /** TRANSIENT/BUG 路径的降级文案（叠加规则查询）。 */
    private static final String STACK_DEGRADED_MESSAGE = "优惠券规则暂时无法获取，请稍后重试";

    @Override
    public CouponClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new CouponClient() {

            @Override
            public Integer useCoupon(Long userId, Long userCouponId, String orderNo,
                                     Integer orderAmount, Integer orderType, Long sellerId,
                                     String spuIds, String categoryIds) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("优惠券核销降级(透传): 下游业务失败 userId={}, userCouponId={}, orderNo={}, code={}, msg={}",
                            userId, userCouponId, orderNo, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("优惠券核销降级: 暂时无法使用（category={}；禁止返回 0——0 会被调用方记成"
                        + "「核销成功但抵扣 0」，用户多付且补偿去回滚一张没核销的券）"
                        + " userId={}, userCouponId={}, orderNo={}", category, userId, userCouponId, orderNo, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, USE_DEGRADED_MESSAGE);
            }

            @Override
            public void rollbackByOrderNo(String orderNo) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("优惠券回滚降级(透传): 下游业务失败 orderNo={}, code={}, msg={}",
                            orderNo, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("优惠券回滚降级: 暂时无法回滚（category={}；静默成功会让兜底对账清掉标记、"
                        + "券被永久吞掉） orderNo={}", category, orderNo, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, ROLLBACK_DEGRADED_MESSAGE);
            }

            @Override
            public List<CouponStackInfoCO> batchGet(List<Long> ids) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("优惠券模板批量查询降级(透传): 下游业务失败 ids={}, code={}, msg={}",
                            ids, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("优惠券模板批量查询降级: 暂时无法获取（category={}；返回空列表会让多券叠加校验被"
                        + "静默跳过） ids={}", category, ids, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, STACK_DEGRADED_MESSAGE);
            }
        };
    }
}
