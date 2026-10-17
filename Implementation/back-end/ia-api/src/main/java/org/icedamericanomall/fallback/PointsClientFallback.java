package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PointsClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 积分发放降级工厂 —— <b>③ 命令（写），fail-closed</b>。
 *
 * <p><b>返回值是什么</b>：服务端 {@code marketing.app.points.PointsManager#addPoints} 返回
 * {@code pointsLogRepository.sumByUserId(userId)} —— 即<b>发放后的积分总余额</b>，
 * <b>不是</b>「本次发放的积分」。因此降级返回 {@code 0L} 并不是「发放了 0 分」，
 * 而是<b>伪造了一条业务事实「该用户积分为 0」</b>：它与「账本里确实是 0 分」不可区分，
 * 任何读它的调用方都会拿到一个假的余额。
 *
 * <p><b>调用方实测（两个，均忽略返回值）</b>：
 * <ul>
 *   <li>{@code user.infrastructure.adapter.NewUserRewardServiceAdapter#award}（新用户建档奖励）：
 *       {@code pointsClient.addPoints(userId, 200, 2, "新用户建档奖励");} —— 返回值丢弃，
 *       但<b>紧接着无条件打 {@code [user][welcome-reward][done]}</b>。降级返回 0L 时这一行
 *       日志会谎报「奖励已发放」，而实际上积分账本根本没写 —— <b>这才是本次要修的静默成功</b>：
 *       状态没坏，可观测性坏了（值班时无法从日志分辨「发了」和「没发」）。</li>
 *   <li>{@code trade.app.order.OrderLifecycleManager#awardOrderRewardPoints}（确认收货奖励）：
 *       {@code pointsClient.addPoints(order.getUserId(), points, 2, "下单奖励");} —— 返回值同样丢弃。</li>
 * </ul>
 * <p>两个调用方<b>都把异常视为正常降级路径</b>：前者 {@code catch (Exception) → log.warn}，
 * 后者 {@code catch (Exception) → log.error}（业务口径本来就是「奖励是旁路动作，
 * 失败不阻断建单/建档」）。故本工厂改为 <b>fail-closed 抛异常不会改变任何业务结果</b>
 * （异常照样被调用方吞掉、主链路照样继续），只是把「谎报的成功日志」换成
 * 「准确的失败日志」，并让下游 4xx 不再被改写：
 * <ul>
 *   <li>{@code category == CLIENT}（如下游参数校验 4xx + 已知 code）→ <b>原样抛
 *       {@link UpstreamException}</b>，保留下游业务码 / 文案 / traceId
 *       （{@code collaboration/09-constraints.md} §三·八·1）；</li>
 *   <li>{@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}
 *       （503 / TRANSIENT / <b>可重试</b>）；<b>不得</b>用
 *       {@code BUSINESS_EXECUTION_EXCEPTION}(3000 / 200 / RULE / 不可重试) ——
 *       那是「业务规则拒绝」的承载，会把可用性问题报成规则问题。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class PointsClientFallback implements FallbackFactory<PointsClient> {

    /** 积分发放降级文案（fail-closed 路径）。 */
    private static final String DEGRADED_MESSAGE = "积分发放暂时无法完成，请稍后重试";

    @Override
    public PointsClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        // 匿名内部类（而非 lambda）：接口新增方法时编译期即报错，见本包 package-info §3。
        return new PointsClient() {

            @Override
            public Long addPoints(Long userId, int points, int type, String source) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("积分发放降级(透传): 下游业务失败 userId={}, points={}, type={}, code={}, msg={}",
                            userId, points, type, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("积分发放降级: 暂时无法完成（category={}；③ 命令 fail-closed，"
                        + "禁止返回 0 —— 该返回值语义是「发放后总余额」，返回 0 等于伪造「积分为 0」）"
                        + " userId={}, points={}, type={}, source={}", category, userId, points, type, source, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
