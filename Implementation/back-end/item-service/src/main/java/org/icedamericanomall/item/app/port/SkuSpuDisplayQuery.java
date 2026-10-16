package org.icedamericanomall.item.app.port;

import org.icedamericanomall.item.app.dto.SkuSpuDisplay;

import java.util.Collection;
import java.util.Map;

/**
 * SKU 所属商品展示事实查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>边界</b>：{@code sku} 与 {@code spu} 同属 item 域，但分属两个聚合 —— 本端口是
 * 「SKU 读视图需要商品列」这一跨聚合读数的隔离点（ADR-057：端口在 {@code app/port/}，
 * 实现在 {@code infrastructure/persistence/}，不外泄到适配层）。
 *
 * <p><b>为何按 ID 批量</b>：I13 一次可带几十个 SKU，只允许打一次数据源，禁止逐行查询（N+1）。
 *
 * <p><b>业务键维度（ADR-058 §3 / ADR-061）</b>：返回值随同一条联表查询带出
 * {@code sku.sku_id} / {@code spu.spu_id} 两个业务键（32-hex），调用方无需二次取数。
 *
 * <p><b>fail-open 归属</b>：本端口不吞异常（真实故障需在调用侧可见）；由调用方
 * {@code SkuManager} 兜住异常并把展示字段留空 —— 展示字段取不到绝不能让购物车/下单取数变脆。
 */
public interface SkuSpuDisplayQuery {

    /**
     * 按 SKU <b>技术键</b>批量查询商品展示事实。
     *
     * @param skuIds SKU 技术键集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return SKU 技术键 → 展示事实。<b>SKU 不存在</b>时该键不出现；<b>SKU 存在但商品行缺失</b>
     *         （脏数据 / 物理删除）时键存在、商品侧字段为 {@code null} —— 两种情况在调用侧
     *         效果一致（商品侧留空、SKU 本体照常返回）
     */
    Map<Long, SkuSpuDisplay> findBySkuIds(Collection<Long> skuIds);

    /**
     * 按 SKU <b>业务键</b>批量查询商品展示事实（ADR-061 §决策 2：入参收业务键）。
     *
     * <p>供 {@code SkuManager} 把线格式的 SKU 业务键解析为技术键 + 商品侧事实，
     * 单条 SQL 走 {@code sku.sku_id} 唯一索引，性能与按技术键等价（A 案：表结构不动）。
     *
     * @param skuBusinessIds SKU 业务键集合（32-hex；可空；实现需去重、忽略 {@code null}
     *                       与格式非法元素 —— 非法入参落「未命中」，不放大成 500）
     * @return SKU 业务键 → 展示事实；无对应行时该键不出现
     */
    Map<String, SkuSpuDisplay> findBySkuBusinessIds(Collection<String> skuBusinessIds);
}
