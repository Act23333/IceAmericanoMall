package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.CartClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CartItemCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车服务降级工厂 —— <b>全部方法 fail-closed（4xx 透传 / 其余抛错）</b>。
 *
 * <p>本 Client 没有任何方法适合「降级返回空值」，理由逐个说明（依据读调用方
 * {@code trade-service NormalCartOrderStrategy} 得出）：
 *
 * <ul>
 *   <li>{@code getSelectedItems} —— <b>判别型查询</b>（② 类）。
 *       {@code NormalCartOrderStrategy#validate} 把「空列表」解释为业务事实
 *       「购物车为空，无法下单」。若这里降级返回空列表，cart-service 抖动就会被伪装成
 *       用户没有选商品 —— 用户明明有货却被告知购物车是空的，属于错误信息污染。
 *       抛异常才能让流程走到「系统繁忙，请稍后重试」。</li>
 *   <li>{@code clearCart} / {@code deleteByIds} —— <b>命令</b>（③ 类）。
 *       静默成功会留下「已下单的购物车项」，用户再次进入购物车即可对同一批商品
 *       重复下单（重复扣款 / 重复占用库存）。调用方 {@code afterCreate} 已把异常当作
 *       「非致命」单独 try/catch 并打 ERROR 日志告警，因此这里抛异常不会阻断主流程。</li>
 * </ul>
 *
 * <p>{@code category == CLIENT} 的 4xx 一律<b>原样抛 {@link UpstreamException}</b>
 * （§三·八·1：下游说「你参数错/购物车项不存在」是业务事实，不得改写成本域
 * {@code SYSTEM_BUSY}）；{@code TRANSIENT} / {@code BUG} 才抛 {@code SYSTEM_BUSY}。
 */
@Slf4j
@Component
public class CartClientFallback implements FallbackFactory<CartClient> {

    /** TRANSIENT/BUG 路径的降级文案 —— 「暂时无法获取」（§三·八·2）。 */
    private static final String DEGRADED_MESSAGE = "购物车暂时无法获取，请稍后重试";

    @Override
    public CartClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new CartClient() {

            @Override
            public List<CartItemCO> getSelectedItems(Long userId) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("购物车服务降级(透传): 下游业务失败 getSelectedItems userId={}, code={}, msg={}",
                            userId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("购物车服务降级: 查询已选商品失败（category={}；禁止降级为空列表，否则会伪装成"
                        + "「购物车为空」） userId={}", category, userId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }

            @Override
            public void clearCart(Long userId) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("购物车服务降级(透传): 下游业务失败 clearCart userId={}, code={}, msg={}",
                            userId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("购物车服务降级: 清空购物车失败（category={}；静默=留下已购项可重复下单）"
                        + " userId={}", category, userId, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }

            @Override
            public void deleteByIds(Long userId, List<Long> cartItemIds) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("购物车服务降级(透传): 下游业务失败 deleteByIds userId={}, cartItemIds={}, code={}, msg={}",
                            userId, cartItemIds, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("购物车服务降级: 删除已购购物车项失败（category={}；静默=留下已购项可重复下单）"
                        + " userId={}, cartItemIds={}", category, userId, cartItemIds, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEGRADED_MESSAGE);
            }
        };
    }
}
