package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单趋势图数据点（AD13 {@code GET /api/trade/admin/stats/trend} 出参元素）。
 *
 * <p><b>批次 D</b>：改造前为 {@code List<Map<String, Object>>}，元素键
 * {@code Map.of("date", date.toString(), "orders", count, "revenue", revenue)}。现改为
 * 强类型 VO，<b>口径与数值未变</b>（纯类型重构）。
 *
 * <p><b>为什么是 {@code List<{date, orders, revenue}>} 而不是 Map</b>：原实现的
 * 「按天」维度是<b>行内 {@code date} 字段</b>（不是把日期当 Map key），故形状本就是
 * 「列表 + 行内日期」，本次<b>无形状变化</b>；仅把每行的弱类型 Map 换成强类型 VO。
 *
 * <p><b>与 {@link SalesByDateVO} 的关系</b>：两者字段集合相同但<b>语义/端点不同</b>
 * —— 本 VO 服务于「最近 N 天（{@code days}，以今天为终点向前推）」的趋势图；
 * {@link SalesByDateVO} 服务于「指定 from/to 区间的分日销售额」。契约 AD13/AD15 也
 * 分别命名（{@code TrendDataVO} / {@code DailySalesVO}），故保持两个类型，不合并 ——
 * 否则任一端的语义演进（如趋势图加 {@code 环比}）会污染另一端。
 *
 * <p><b>单位（本批钉死）</b>：{@link #revenue} 为<b>整数分</b>。
 */
@Data
public class OrderTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 统计日期，格式 {@code yyyy-MM-dd}（线上键 {@code date}）。
     *
     * <p>「当天」取 <b>JVM 默认时区</b>（代码用 {@code LocalDate.now()}，未硬编码时区；
     * 部署侧 DB 连接固定 {@code serverTimezone=Asia/Shanghai}）。
     */
    private String date;

    /**
     * 当日下单数（{@code orders.create_time ∈ [当日 00:00, 次日 00:00)}，<b>全状态</b>计数）。
     *
     * <p>与 {@link #revenue} 口径不对称（前者全状态、后者仅已完成）属既有实现口径，
     * 本批未改 —— 见 {@link AdminDashboardVO} 同类说明。
     */
    private long orders;

    /**
     * 当日营收 = 当日<b>已完成</b>订单的 {@code total_amount} 合计。
     *
     * <p><b>单位：分</b>。商品<b>原价</b>口径（不含运费、未减优惠），与结算单
     * {@code settlement_amount}（{@code payAmount} 口径）<b>不同源</b>。
     */
    private Integer revenue;
}
