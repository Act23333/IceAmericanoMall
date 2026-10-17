package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单服务（trade-service）降级工厂 —— <b>全部方法 fail-closed（抛异常）</b>。
 *
 * <p>本 Client 服务于「资金 + 订单状态」链路，任何一个方法的静默降级都会造成资金 / 状态不一致：
 *
 * <ul>
 *   <li>{@code updateOrderStatus} —— <b>命令</b>（③ 类）。pay-service 支付成功后靠它把订单
 *       推进到「待发货」。静默成功 = 用户已付款但订单永远停在「待付款」，
 *       后续超时任务还会按未支付取消订单 → <b>钱货两空</b>。必须让调用方感知失败以触发重试 / 补偿。</li>
 *   <li>{@code getOrder} —— <b>判别型查询</b>（② 类）。调用方用它取订单金额发起支付；
 *       降级返回 {@code null} 会被解释为「订单不存在」，或生成金额为 0 的支付单，
 *       同样属于错误信息污染，必须抛异常。</li>
 *   <li>{@code createOrder} —— <b>命令</b>（③ 类）。marketing-service 秒杀 Redis 预扣后委托落库；
 *       降级返回 {@code null} 会让营销侧以为「下单成功」（实际 Redis 库存已扣、订单未落库）
 *       → 用户拿不到订单却已被占用秒杀资格。</li>
 * </ul>
 *
 * <p><b>分支（{@code 09-constraints} §三·八·1 透传优先）</b>：
 * <ul>
 *   <li>{@code category == CLIENT} 且异常链中带已类型化的下游错误 → <b>原样抛
 *       {@link UpstreamException}</b>（下游 4xx + 已知 {@code code} 是<b>业务事实</b>，
 *       改写成「服务不可用」会让调用方做必然失败的重试）；</li>
 *   <li>{@code TRANSIENT} / {@code BUG} → <b>原有 fail-closed 口径一字不变</b>：
 *       {@code BizException(SYSTEM_BUSY)}（503 / {@code TRANSIENT} / 可重试）。</li>
 * </ul>
 * 两条分支都仍然是「抛异常」—— 本 Client <b>不存在静默成功</b>的路径。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class OrderClientFallback implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new OrderClient() {

            @Override
            public void updateOrderStatus(String orderNo, Integer status) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("订单服务降级(透传): 下游业务失败 orderNo={}, targetStatus={}, code={}, msg={}",
                            orderNo, status, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("订单服务降级：更新订单状态失败 orderNo={}, targetStatus={}", orderNo, status, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，订单状态未更新");
            }

            @Override
            public OrderSummaryCO getOrder(String orderNo) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("订单服务降级(透传): 下游业务失败 orderNo={}, code={}, msg={}",
                            orderNo, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("订单服务降级：查询订单失败（禁止降级为 null，调用方会误判为「订单不存在」）"
                        + " orderNo={}", orderNo, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，请稍后重试");
            }

            @Override
            public OrderSummaryCO getOrderById(Long orderId) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("订单服务降级(透传): 下游业务失败 orderId={}, code={}, msg={}",
                            orderId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("订单服务降级：按 id 查询订单失败（归属校验 fail-closed，禁止放行）"
                        + " orderId={}", orderId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，请稍后重试");
            }

            @Override
            public OrderSummaryCO createOrder(CreateOrderInternalReq req) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("订单服务降级(透传): 下游业务失败 userId={}, flashId={}, code={}, msg={}",
                            req == null ? null : req.getUserId(),
                            req == null ? null : req.getFlashId(),
                            upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("订单服务降级：创建订单失败 userId={}, flashId={}",
                        req == null ? null : req.getUserId(),
                        req == null ? null : req.getFlashId(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，下单失败");
            }
        };
    }
}
