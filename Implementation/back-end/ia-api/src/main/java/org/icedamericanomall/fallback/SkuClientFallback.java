package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.SkuClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.dto.StockOpCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SKU 服务（item-service）降级工厂 —— <b>读 / 写分治，结论是三个方法全部 fail-closed</b>。
 *
 * <p><b>分治结论（先读方法签名，再读调用方）</b>：
 * <table border="1">
 *   <caption>逐方法定类</caption>
 *   <tr><th>方法</th><th>方向</th><th>定类</th><th>降级行为</th></tr>
 *   <tr><td>{@code deductStock}</td><td>写（扣减）</td><td>③ 命令</td><td>fail-closed</td></tr>
 *   <tr><td>{@code restoreStock}</td><td>写（回补）</td><td>③ 命令</td><td>fail-closed</td></tr>
 *   <tr><td>{@code getSkuListByIds}</td><td>读</td><td><b>② 判别型查询</b></td><td>fail-closed</td></tr>
 * </table>
 * <p><b>为什么这个「读」不按 fail-open 处理</b>：本 Client 唯一的读方法不是展示型查询 ——
 * 空列表会被调用方翻译成一条<b>业务事实</b>。三个下单策略
 * （{@code NormalCartOrderStrategy} / {@code DirectOrderStrategy} /
 * {@code FlashSaleOrderStrategy} 及 {@code GroupBuyOrderStrategy}）都是
 * {@code if (skuList.isEmpty() || skuList.size() != 期望条数) throw「SKU 不存在」}
 * 的形态：降级返回空列表，用户会被告知「商品不存在」（{@code OrderStrategySkuNotFoundTest}
 * 锁死了这条语义），而真实原因是商品服务挂了 —— 这正是本包 {@code package-info} §2
 * 定义的 <b>② 判别型查询</b>（「错误信息污染」最典型的一类），必须 fail-closed。
 * 换言之：本 Client <b>没有</b>「返回空值只影响少展示一点」的方法，
 * 故不存在按 fail-open 处理的分支。
 *
 * <p><b>为什么写方向必须 fail-closed</b>：
 * <ul>
 *   <li>{@code deductStock} —— 资金安全关键路径。调用方
 *       {@code OrderCreationManager#deductStock} 的 {@code catch (Exception)} 会转成
 *       {@code BizException(STOCK_INSUFFICIENT, "库存扣减失败，请重试")} 并触发 Saga 补偿；
 *       静默成功 = 订单正常落库但库存从未扣减 → <b>超卖</b>。</li>
 *   <li>{@code restoreStock} —— 取消 / 超时 / 建单失败补偿三条链路靠它归还库存
 *       （{@code OrderLifecycleManager#restoreStock}、{@code GroupBuyParticipationService}、
 *       {@code OrderCreationManager#compensateOnFailure}），三处均
 *       {@code catch (Exception) → log.error「需人工处理」}。静默成功 = 取消订单后库存永久丢失
 *       （少卖）；抛异常是让该告警生效的前提。</li>
 * </ul>
 *
 * <p><b>横向铁律：4xx 透传（{@code 09-constraints} §三·八·1）</b>：三个方法都先判
 * {@code category == CLIENT && upstream != null} → <b>原样抛 {@link UpstreamException}</b>
 * （保留下游业务码 / 文案 / traceId，例如「库存不足」这类业务事实不能被改写成「服务不可用」）；
 * {@code TRANSIENT} / {@code BUG} → 抛 {@code BizException(SYSTEM_BUSY)}（503 / TRANSIENT /
 * <b>可重试</b>）。<b>不得</b>用 {@code BUSINESS_EXECUTION_EXCEPTION}(3000 / 200 / RULE /
 * 不可重试) —— 那是「业务规则拒绝」的承载，会把可用性问题报成规则问题。
 * <p>已核实透传在这些调用点不会丢语义：三处库存调用都是 {@code catch (Exception)} 宽兜底，
 * 故 {@code UpstreamException}（{@code RuntimeException}）与今天的 {@code BizException}
 * 走同一条补偿/告警分支。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class SkuClientFallback implements FallbackFactory<SkuClient> {

    /** 批量 SKU 查询降级文案（② 判别型：空列表会被误判为「SKU 不存在」）。 */
    private static final String QUERY_DEGRADED_MESSAGE = "商品服务暂不可用，请稍后重试";

    /** 扣减库存降级文案。 */
    private static final String DEDUCT_DEGRADED_MESSAGE = "库存扣减失败，请重试";

    /** 恢复库存降级文案。 */
    private static final String RESTORE_DEGRADED_MESSAGE = "库存恢复失败，请稍后重试";

    @Override
    public SkuClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new SkuClient() {

            @Override
            public List<SkuCO> getSkuListByIds(List<String> skuIds) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("SKU 批量查询降级(透传): 下游业务失败 skuIds={}, code={}, msg={}",
                            skuIds, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("SKU 服务降级：批量查询 SKU 失败（② 判别型——空列表会被调用方误判为"
                        + "「SKU 不存在」，故 fail-closed） skuIds={}, category={}", skuIds, category, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, QUERY_DEGRADED_MESSAGE);
            }

            @Override
            public void deductStock(List<StockOpCO> items) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("SKU 扣减库存降级(透传): 下游业务失败 items={}, code={}, msg={}",
                            items, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("SKU 服务降级：扣减库存失败（③ 命令 fail-closed——静默将导致超卖）"
                        + " items={}, category={}", items, category, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, DEDUCT_DEGRADED_MESSAGE);
            }

            @Override
            public void restoreStock(List<StockOpCO> items) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("SKU 恢复库存降级(透传): 下游业务失败 items={}, code={}, msg={}",
                            items, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("SKU 服务降级：恢复库存失败（③ 命令 fail-closed——静默会使取消订单后"
                        + "库存永久丢失，需人工介入核对） items={}, category={}", items, category, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, RESTORE_DEGRADED_MESSAGE);
            }
        };
    }
}
