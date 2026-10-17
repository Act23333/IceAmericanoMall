package org.icedamericanomall.trade.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 商家可提现余额视图对象（T22 {@code GET /api/seller/finance/balance} 出参）。
 *
 * <p><b>批次 D</b>：改造前该端点在 <b>Controller 内</b>就地拼 {@code Map<String,Object>}
 * （{@code Map.of("sellerId", sellerId, "balance", settlementManager.availableBalance(sellerId))}）——
 * 适配层拼弱类型报文，违反「Controller 只做参数校验 + 调用 Manager + 包 Result」。
 * 现由 {@code SettlementManager} 装配本 VO，Controller 直接透出。
 *
 * <p><b>线上键（BREAKING）</b>：全局 {@code SNAKE_CASE} 使 {@code sellerId} 渲染为
 * {@code seller_id} —— 与契约 T22 已声明的 {@code {seller_id, balance}} <b>对齐</b>；
 * 改造前实现发的是 {@code sellerId}（camelCase），与契约不符。实测前端零消费该端点。
 *
 * <p><b>单位（本批钉死）</b>：{@link #balance} 为<b>整数分</b>。
 */
@Data
public class SellerBalanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商家标识 = 商家账号的 <b>{@code user_id}</b>（{@code seller.user_id}，与
     * {@code UserContext.getUserId()} 同源）。
     *
     * <p>⚠️ <b>不是</b> {@code seller.id} 技术主键 —— 全平台对外 seller 口径见契约 §1.8。
     */
    private Long sellerId;

    /**
     * 可提现余额 = <b>所有已结算未打款</b>结算单
     * （{@code settlement.status = SettlementStatusEnum.SETTLED}）的
     * {@code settlement_amount} 合计。
     *
     * <p><b>单位：分</b>（{@code 1280000} = 12800.00 元）。
     *
     * <p><b>口径（如实登记）</b>：{@code settlement_amount = totalAmount − commission}，
     * 佣金为 5%（{@code totalAmount × 5 / 100}），而结算单的 {@code totalAmount} 取
     * <b>买家实付 {@code payAmount}</b>（含运费、已减优惠）。故本字段与看板类
     * {@code revenue}（{@code orders.total_amount} 原价口径、不含运费）<b>不同源</b>，
     * 两者不可互相校验。
     *
     * <p><b>未扣减在途提现</b>：已申请但未打款的提现单不改变结算单状态，故本值不随提现申请减少
     * （既有实现口径，本批未改）。
     */
    private Integer balance;
}
