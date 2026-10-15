package org.icedamericanomall.item.app.port;

import org.icedamericanomall.item.app.dto.SkuProductDisplay;

import java.util.Collection;
import java.util.Map;

/**
 * SKU 所属商品展示事实查询（应用层端口，读路径 CQRS）。
 *
 * <p><b>边界</b>：{@code sku} 与 {@code product} 同属 item 域，但分属两个聚合 —— 本端口是
 * 「SKU 读视图需要商品列」这一跨聚合读数的隔离点（ADR-057：端口在 {@code app/port/}，
 * 实现在 {@code infrastructure/persistence/}，不外泄到适配层）。
 *
 * <p><b>为何按 ID 批量</b>：I13 一次可带几十个 SKU，只允许打一次数据源，禁止逐行查询（N+1）。
 *
 * <p><b>fail-open 归属</b>：本端口不吞异常（真实故障需在调用侧可见）；由调用方
 * {@code SkuManager} 兜住异常并把四字段留空 —— 展示字段取不到绝不能让购物车/下单取数变脆。
 */
public interface SkuProductDisplayQuery {

    /**
     * 按 SKU ID 批量查询商品展示事实。
     *
     * @param skuIds SKU ID 集合（可空；实现需去重并忽略 {@code null} 元素）
     * @return SKU ID → 展示事实。<b>SKU 不存在</b>时该键不出现；<b>SKU 存在但商品行缺失</b>
     *         （脏数据 / 物理删除）时键存在、四字段为 {@code null} —— 两种情况在调用侧
     *         效果一致（四字段留空、SKU 本体照常返回）
     */
    Map<Long, SkuProductDisplay> findBySkuIds(Collection<Long> skuIds);
}
