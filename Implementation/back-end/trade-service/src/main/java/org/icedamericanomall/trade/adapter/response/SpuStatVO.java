package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品（SPU）维度销量 / 销售额统计行（S11 {@code GET /api/trade/seller/dashboard/product-stats}
 * 与 AD14 {@code GET /api/trade/admin/stats/top-products} <b>共用</b>出参元素）。
 *
 * <p><b>批次 D</b>：两个端点在改造前均返回 {@code List<Map<String, Object>>}，元素键由
 * {@code SellerDashboardManager.spuStats} / {@code AdminManager.topSpus} 逐行
 * {@code Map.of("spu_name", …, "quantity", …, "revenue", …)} 拼装。现统一为强类型 VO，
 * <b>聚合口径与数值未变</b>（纯类型重构）。两处端点用<b>同一个</b> VO 表达同一形状的统计行，
 * 避免再出现「同名字段两处独立定义」的漂移面。
 *
 * <p><b>线上键（红线）</b>：字段 {@code spuName} 在全局 {@code SNAKE_CASE} 策略下渲染为
 * {@code spu_name} —— 与契约 §1.8④「凡报文中表示某个 SPU 名字的字段，键名一律
 * {@code spu_name}」一致，且与改造前的 Map 字面键 <b>逐字节相同</b>（该端点的键名在本批
 * <b>无 BREAKING</b>；B5 已把旧 {@code productName} 收口为 {@code spu_name}）。
 *
 * <p><b>单位（本批钉死）</b>：{@link #revenue} 为<b>整数分</b>。此前该键在任何文档里
 * 都没有单位声明（契约只写「spu_name/quantity/revenue」），本批明确为「分」，
 * 与 {@code order_item.sub_total}（分）同源，不做任何换算。
 */
@Data
public class SpuStatVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SPU 名称（{@code order_item.spu_name} <b>快照</b>）。
     *
     * <p>线上键恒为 {@code spu_name}（契约 §1.8④ / ADR-061 §决策 5）。
     *
     * <p><b>聚合键口径（如实登记，本批未改算法）</b>：两个端点都以<b>快照名称</b>为聚合键，
     * 因此同一个 SPU 若在历史订单里存在多个快照名（改名前后），会被拆成多行；反之不同的
     * SPU 若同名会被合并。该行为属既有实现口径，本批**只换类型、不动算法**。
     */
    private String spuName;

    /** 销量（{@code order_item.quantity} 累加，单位：<b>件</b>）。 */
    private Integer quantity;

    /**
     * 销售额（{@code order_item.sub_total} 累加）。
     *
     * <p><b>单位：分</b>（{@code 25600} = 256.00 元）。{@code sub_total} 为下单时的
     * 单价快照 × 数量，故本字段是<b>成交口径</b>而非现价口径；不含运费（ADR-037）。
     *
     * <p>⚠️ <b>两个端点的取数基数不同（如实登记，本批未改算法 / 待产品裁决）</b>：
     * <ul>
     *   <li>S11 {@code /api/trade/seller/dashboard/product-stats} ——
     *       {@code orderRepository.findBySellerAndCreateTimeAfter(...)}，
     *       <b>不限状态</b>（含待付款 / 待发货 / <b>已取消</b>的订单项）；</li>
     *   <li>AD14 {@code /api/trade/admin/stats/top-products} ——
     *       {@code orderRepository.findByStatus(COMPLETED)}，<b>仅已完成</b>。</li>
     * </ul>
     * 因此同一个 VO 在两端的 {@code revenue} <b>不可横向对比</b>，S11 的「销售额」在语义上
     * 更接近「下单额」。该差异属既有实现（本批为纯类型重构，不改业务算法），
     * 已在交付报告中作为「待裁决口径」登记。
     */
    private Integer revenue;
}
