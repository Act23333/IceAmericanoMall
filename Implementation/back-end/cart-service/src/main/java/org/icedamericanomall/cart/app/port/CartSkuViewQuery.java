package org.icedamericanomall.cart.app.port;

import org.icedamericanomall.cart.app.dto.SkuView;

import java.util.Collection;
import java.util.Map;

/**
 * 购物车 SKU 视图查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>边界（ADR-057）</b>：端口定义在应用层 {@code app/port/}，实现在
 * {@code infrastructure/persistence/}，不外泄到适配层；只读（仅 SELECT），
 * 不改写被读域数据；<b>仅作展示与键解析</b>，不得作为下单/退款的业务判定依据
 * （业务判定仍走 I13 Feign：{@code NormalCartOrderStrategy} fail-closed）。
 *
 * <p><b>两种入参口径（ADR-061 §决策 2 + A 案）</b>：
 * <ul>
 *   <li>{@link #findBySkuIds} —— 按 cart 行持有的<b>技术键</b>批量回填（读路径）；</li>
 *   <li>{@link #findBySkuBusinessIds} —— 按线格式的<b>业务键</b>批量解析（写路径入参）；
 *       走 {@code sku.sku_id} 唯一索引，性能与按技术键等价。</li>
 * </ul>
 * 两个方向都<b>一次</b>查询完成（禁止逐行），且同一条 SQL 同时返回技术键与业务键。
 *
 * <p><b>fail-open 归属</b>：本端口不吞异常（真实故障需在调用侧可见）；由
 * {@code CartManager} 兜住异常并置空字段/按未命中处理 —— 展示回填取不到绝不能让
 * 购物车列表变脆。
 */
public interface CartSkuViewQuery {

    /**
     * 按 SKU <b>技术键</b>批量查询（读路径：cart 行 → 展示列 + 业务键）。
     *
     * @param skuTechnicalIds SKU 技术键集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return 技术键 → SKU 视图；不存在的 SKU 不出现在 Map 中
     */
    Map<Long, SkuView> findBySkuIds(Collection<Long> skuTechnicalIds);

    /**
     * 按 SKU <b>业务键</b>批量查询（写路径：线格式入参 → 技术键 + 展示列）。
     *
     * @param skuBusinessIds SKU 业务键集合（32-hex；可空；实现需去重并忽略 {@code null}
     *                       与格式非法元素 —— 非法入参落「未命中」，不放大成 500）
     * @return 业务键 → SKU 视图；不存在的 SKU 不出现在 Map 中
     */
    Map<String, SkuView> findBySkuBusinessIds(Collection<String> skuBusinessIds);
}
