package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 商家看板视图对象（S10 {@code GET /api/trade/seller/dashboard} 出参）。
 *
 * <p><b>批次 D（{@code Result<Map>} → 类型化 VO）</b>：本端点在 B 批及其之前返回
 * {@code Result<Map<String, Object>>}，键由 {@code SellerDashboardManager} 以
 * {@code LinkedHashMap.put("todayOrderCount", …)} 方式拼装 —— 属 09-constraints
 * 「Controller / Manager 返回 Map」反模式（弱类型、无单位声明、契约无法校验）。
 * 现改为强类型 VO，<b>聚合口径与数值一个字节都没动</b>（纯类型重构）。
 *
 * <p><b>线上键（红线）</b>：全局 {@code spring.jackson.property-naming-strategy: SNAKE_CASE}
 * 作用于 JSON，故 Java 字段 {@code todayOrderCount} 的线上键恒为 {@code today_order_count}。
 * 改造前 Map 的字面键是 <b>camelCase</b>（{@code todayOrderCount}），改造后为
 * <b>snake_case</b> —— 与契约 S10 已声明的
 * {@code {today_order_count, pending_ship_count, monthly_revenue}} <b>对齐</b>，
 * 属**线上键 BREAKING**（此前实现与契约不符；实测前端零消费该端点）。
 *
 * <p><b>单位（本批钉死）</b>：金额字段一律<b>整数分</b>，字段名不含单位后缀，
 * 单位只在本 javadoc 与契约里声明（全平台口径一致，禁止在此处混入「元」）。
 */
@Data
public class SellerDashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 今日订单数（{@code orders.create_time >= 今日零点}，<b>全状态</b>计数，单位：笔）。 */
    private long todayOrderCount;

    /** 待发货订单数（{@code orders.status = OrderStatusEnum.PENDING_SHIPMENT}，单位：笔）。 */
    private long pendingShipCount;

    /**
     * 本月营收（{@code orders.create_time >= 本月 1 日零点} 且已完成的订单
     * {@code total_amount} 合计）。
     *
     * <p><b>单位：分</b>（{@code 12800} = 128.00 元）。取值口径 = {@code orders.total_amount}
     * 累加，即<b>商品原价口径</b>：不含运费（ADR-037：{@code post_fee} 单列于
     * {@code orders.post_fee}），<b>也未减</b>优惠/优惠券（那是 {@code payAmount} 口径）。
     *
     * <p>⚠️ 因此本字段与 {@code SellerBalanceVO.balance}（结算单 {@code payAmount} 口径）
     * <b>不同源、不可互相校验</b> —— 后者含运费且已减优惠后再扣 5% 佣金。
     */
    private Integer monthlyRevenue;
}
