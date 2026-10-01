package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CartItemCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车服务降级工厂 —— <b>全部方法 fail-closed（抛异常）</b>。
 *
 * <p>本 Client 没有任何方法适合「降级返回空值」，理由逐个说明：
 *
 * <ul>
 *   <li>{@code getSelectedItems} —— <b>判别型查询</b>（② 类）。
 *       调用方 {@code NormalCartOrderStrategy#validate} 把「空列表」解释为业务事实
 *       「购物车为空，无法下单」。若这里降级返回空列表，cart-service 抖动就会被伪装成
 *       用户没有选商品 —— 用户明明有货却被告知购物车是空的，属于错误信息污染。
 *       抛异常才能让流程走到「系统繁忙，请稍后重试」。</li>
 *   <li>{@code clearCart} / {@code deleteByIds} —— <b>命令</b>（③ 类）。
 *       静默成功会留下「已下单的购物车项」，用户再次进入购物车即可对同一批商品
 *       重复下单（重复扣款 / 重复占用库存）。调用方 {@code NormalCartOrderStrategy#afterCreate}
 *       已把异常当作「非致命」单独 try/catch 并打 ERROR 日志告警，
 *       因此这里抛异常不会阻断主流程，只是把故障暴露出来。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class CartClientFallback implements FallbackFactory<CartClient> {

    @Override
    public CartClient create(Throwable cause) {
        return new CartClient() {

            @Override
            public List<CartItemCO> getSelectedItems(Long userId) {
                log.error("购物车服务降级：查询已选商品失败（禁止降级为空列表，否则会伪装成「购物车为空」）"
                        + " userId={}", userId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "购物车服务暂不可用，请稍后重试");
            }

            @Override
            public void clearCart(Long userId) {
                log.error("购物车服务降级：清空购物车失败 userId={}", userId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "购物车服务暂不可用，请稍后重试");
            }

            @Override
            public void deleteByIds(Long userId, List<Long> cartItemIds) {
                log.error("购物车服务降级：删除已购购物车项失败 userId={}, cartItemIds={}",
                        userId, cartItemIds, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "购物车服务暂不可用，请稍后重试");
            }
        };
    }
}
