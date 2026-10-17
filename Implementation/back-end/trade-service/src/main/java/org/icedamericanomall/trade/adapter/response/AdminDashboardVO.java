package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台仪表盘视图对象（AD11 {@code GET /api/trade/admin/dashboard} 出参）。
 *
 * <p><b>批次 D</b>：改造前返回 {@code Result<Map<String, Object>>}（{@code AdminManager}
 * 以 {@code HashMap.put("totalUsers", …)} 拼装，6 个 camelCase 字面键）。现改为强类型 VO，
 * <b>聚合口径与数值未变</b>（纯类型重构）。
 *
 * <p><b>线上键（BREAKING）</b>：全局 {@code SNAKE_CASE} 使 {@code totalUsers} 渲染为
 * {@code total_users} 等 —— 与契约 AD11 的样例键对齐；改造前的实现发的是 camelCase，
 * 与契约不符。实测前端（`Implementation/front-end/**`）<b>零消费</b>该端点，故实际影响面为零，
 * 仍如实登记为线上键 BREAKING。
 *
 * <p><b>与契约样例的偏差（如实登记，不臆造）</b>：契约 AD11 样例写有
 * {@code today_new_users} 与 {@code pending_seller_count}，而<b>代码从未产出</b>这两个指标
 * （涉及 user-service 今日新增用户数与商家待审数，属跨域聚合，本批不新增业务）；
 * 反之代码产出的 {@code completed_orders} 契约样例里没有。本批以<b>代码实际产出的键</b>为准，
 * 偏差在契约侧订正（见【变更交接摘要】）。
 *
 * <p><b>单位（本批钉死）</b>：所有金额字段为<b>整数分</b>。
 */
@Data
public class AdminDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台注册用户总数（{@code user-service} 的 {@code GET /internal/user/count} 结果）。
     *
     * <p>跨服务读取<b>fail-open</b>：user-service 不可用时该值落 {@code 0}（既有实现口径，
     * 本批未改），不得据此判定「平台无用户」。
     */
    private long totalUsers;

    /** 平台订单总数（{@code orders} 全状态行数，单位：笔）。 */
    private long totalOrders;

    /** 已完成订单数（{@code orders.status = OrderStatusEnum.COMPLETED}，单位：笔）。 */
    private long completedOrders;

    /**
     * 累计营收 = <b>全部已完成订单</b>的 {@code total_amount} 合计。
     *
     * <p><b>单位：分</b>。商品<b>原价</b>口径：不含运费（ADR-037：{@code post_fee} 单列于
     * {@code orders.post_fee}），也未减优惠（那是 {@code payAmount} 口径）。因此与结算单
     * {@code settlement_amount}（{@code payAmount} 口径、再扣 5% 佣金）<b>不同源</b>。
     */
    private Integer totalRevenue;

    /**
     * 今日订单数（{@code orders.create_time >= 今日零点}，<b>全状态</b>计数，单位：笔）。
     *
     * <p>注意与 {@link #todayRevenue} 的口径差异：前者全状态，后者只看已完成 ——
     * 属既有实现口径（同日「下单量 vs 成交额」两把尺），本批未改。
     */
    private long todayOrders;

    /**
     * 今日营收 = <b>今日已完成订单</b>的 {@code total_amount} 合计。
     *
     * <p><b>单位：分</b>。不含运费。
     */
    private Integer todayRevenue;
}
