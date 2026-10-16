package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.dto.StockOpCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SKU 服务（item-service）降级工厂 —— <b>全部方法 fail-closed（抛异常）</b>。
 *
 * <p>本 Client 是「库存」的唯一出入口，静默降级的后果是超卖 / 库存丢失，故一律抛异常：
 *
 * <ul>
 *   <li>{@code getSkuListByIds} —— <b>判别型查询</b>（② 类）。
 *       三个调用方（{@code NormalCartOrderStrategy} / {@code DirectOrderStrategy} /
 *       {@code FlashSaleOrderStrategy}）都把「空列表 / 长度不匹配」直接翻译成业务事实
 *       「SKU 不存在」。降级返回空列表会向用户谎报商品不存在，必须抛异常。</li>
 *   <li>{@code deductStock} —— <b>命令</b>（③ 类），且是<b>资金安全关键路径</b>。
 *       调用方 {@code OrderCreationManager#createOrder} 把异常转成
 *       「库存扣减失败，请重试」并触发 Saga 回滚；若降级静默成功，
 *       订单会正常落库但库存从未扣减 → <b>超卖</b>。</li>
 *   <li>{@code restoreStock} —— <b>命令</b>（③ 类）。取消 / 超时 / 建单失败补偿三条链路都靠它归还库存；
 *       静默成功会让取消订单后库存永久丢失（少卖）。调用方已 try/catch 并打
 *       「需人工处理」告警日志，抛异常是让该告警生效的前提。</li>
 * </ul>
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SkuClientFallback implements FallbackFactory<SkuClient> {

    @Override
    public SkuClient create(Throwable cause) {
        return new SkuClient() {

            @Override
            public List<SkuCO> getSkuListByIds(List<String> skuIds) {
                log.error("SKU 服务降级：批量查询 SKU 失败（禁止降级为空列表，调用方会误判为「SKU 不存在」）"
                        + " skuIds={}", skuIds, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "商品服务暂不可用，请稍后重试");
            }

            @Override
            public void deductStock(List<StockOpCO> items) {
                log.error("SKU 服务降级：扣减库存失败（静默将导致超卖） items={}", items, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "库存扣减失败，请重试");
            }

            @Override
            public void restoreStock(List<StockOpCO> items) {
                log.error("SKU 服务降级：恢复库存失败（需要人工介入核对） items={}", items, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "库存恢复失败，请稍后重试");
            }
        };
    }
}
