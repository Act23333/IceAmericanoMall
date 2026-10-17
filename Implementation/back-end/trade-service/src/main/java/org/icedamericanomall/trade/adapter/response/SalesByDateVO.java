package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 指定日期区间的分日销售额数据点（AD15 {@code GET /api/trade/admin/stats/sales}
 * 出参元素）。
 *
 * <p><b>批次 D</b>：改造前为 {@code List<Map<String, Object>>}，元素键
 * {@code Map.of("date", d.toString(), "orders", count, "revenue", revenue)}。现改为
 * 强类型 VO，<b>口径与数值未变</b>（纯类型重构）。
 *
 * <p><b>形状说明</b>：按天维度是<b>行内 {@code date} 字段</b>（并非把日期当 Map key），
 * 故「{@code List<{date, value}>}」本就是这个端点的既有形状，本次<b>无形状变化</b>。
 *
 * <p><b>与 {@link OrderTrendVO} 的差异</b>：本 VO 的区间由调用方以 {@code from}/{@code to}
 * 显式给出（左闭右闭，逐日枚举，含端点）；{@link OrderTrendVO} 的区间是「最近 N 天，
 * 以今天为终点」。字段集合相同、语义不同，故不合并（契约 AD15 独立命名
 * {@code DailySalesVO}）。
 *
 * <p><b>单位（本批钉死）</b>：{@link #revenue} 为<b>整数分</b>。
 */
@Data
public class SalesByDateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 统计日期，格式 {@code yyyy-MM-dd}（线上键 {@code date}）。
     *
     * <p>「当天」取 <b>JVM 默认时区</b>（代码用 {@code LocalDate.now()}，未硬编码时区；
     * 部署侧 DB 连接固定 {@code serverTimezone=Asia/Shanghai}）。
     */
    private String date;

    /**
     * 当日下单数（{@code orders.create_time ∈ [当日 00:00, 次日 00:00)}，<b>全状态</b>计数，
     * 单位：笔）。
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
