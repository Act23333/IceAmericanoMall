package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CreateOrderInternalReq;
import org.icedamericanomall.dto.OrderSummaryCO;
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
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class OrderClientFallback implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        return new OrderClient() {

            @Override
            public void updateOrderStatus(String orderNo, Integer status) {
                log.error("订单服务降级：更新订单状态失败 orderNo={}, targetStatus={}", orderNo, status, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，订单状态未更新");
            }

            @Override
            public OrderSummaryCO getOrder(String orderNo) {
                log.error("订单服务降级：查询订单失败（禁止降级为 null，调用方会误判为「订单不存在」）"
                        + " orderNo={}", orderNo, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，请稍后重试");
            }

            @Override
            public OrderSummaryCO createOrder(CreateOrderInternalReq req) {
                log.error("订单服务降级：创建订单失败 userId={}, flashId={}",
                        req == null ? null : req.getUserId(),
                        req == null ? null : req.getFlashId(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "订单服务暂不可用，下单失败");
            }
        };
    }
}
