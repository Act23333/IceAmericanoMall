package org.icedamericanomall.trade.app.port;

import org.icedamericanomall.trade.domain.model.order.SkuCurrentState;
import org.icedamericanomall.trade.domain.model.order.SpuCurrentState;

import java.util.Collection;
import java.util.Map;

/**
 * 订单项当前状态查询（应用层端口，只读展示读模型 —— ADR-061 §决策 3 / ADR-057）。
 *
 * <p><b>定位：读时增强（read-time enrichment），不是订单域事实。</b>
 * 它<b>不写入订单</b>、不改变任何金额、不改变订单状态机；输出<b>仅用于展示</b>，
 * 禁止作为下单/退款/售后等业务判定的依据（业务判断仍须走 Feign 或本域数据）。
 *
 * <p><b>实现约束</b>：
 * <ul>
 *   <li>实现在基础设施层 {@code infrastructure/persistence/}，<b>不外泄</b>到适配层；</li>
 *   <li>按<b>业务键集合</b>批量入参（整页一次查询，禁止逐行调用，无 N+1）；</li>
 *   <li>只读 SELECT，不得改写被读域数据；</li>
 *   <li>本接口<b>不吞异常</b>（真实故障须在调用侧可见）—— fail-open 由
 *       {@code OrderCurrentStateEnricher} 统一兜住：异常 → 空 Map + WARN，
 *       明细<b>照常返回</b>（「不缩行」）。</li>
 * </ul>
 *
 * <p><b>为何按业务键而非技术键</b>：订单项自持业务键（ADR-061 §决策 1），且历史订单
 * 不依赖 {@code sku} 行仍然存在 —— 业务键入参让「SKU 已删、SPU 尚在」也能落到 SPU 粒度比对。
 *
 * <p><b>返回类型的归属</b>：{@link SkuCurrentState} / {@link SpuCurrentState} 定义在
 * <b>领域层</b> {@code domain.model.order} —— 比对规则（{@code OrderItemPriceComparator}）
 * 是领域职责，其入参类型必须由领域层拥有，否则领域服务反向依赖应用层端口
 * （architecture-tests {@code domainDependsOnNothingOutward} 守卫，ADR-038）。
 *
 * @see SkuCurrentState
 * @see SpuCurrentState
 */
public interface OrderItemCurrentStateQuery {

    /**
     * 按 SKU 业务键批量查当前状态。
     *
     * @param skuBusinessIds SKU 业务键集合（32-hex；可空；实现需去重并忽略 {@code null}
     *                       与格式非法元素）
     * @return SKU 业务键 → 当前状态；<b>行不存在（物理删除）时该键不出现</b>（不伪造）
     */
    Map<String, SkuCurrentState> findSkuStates(Collection<String> skuBusinessIds);

    /**
     * 按 SPU 业务键批量查当前状态。
     *
     * @param spuBusinessIds SPU 业务键集合（32-hex；可空；实现需去重并忽略 {@code null}
     *                       与格式非法元素）
     * @return SPU 业务键 → 当前状态；行不存在时该键不出现
     */
    Map<String, SpuCurrentState> findSpuStates(Collection<String> spuBusinessIds);
}
