package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户优惠券视图对象（适配层出参）。
 *
 * <p>字段口径（契约 §4.10.1，05-API-Specification 为唯一真相源）：
 * {@code id}/{@code coupon_id}/{@code status}/{@code used_order_no}/{@code use_time}/
 * {@code create_time} 为持券聚合本体字段；{@code name}/{@code value}/{@code min_amount}/
 * {@code discount_type} 为<b>券面展示字段</b>，由 {@code UserCouponViewAssembler} 按券模板 ID
 * 批量回填（ADR-057：批量 + fail-open；查不到模板 → 字段为 null，券列表照常返回）。
 *
 * <p><b>双主键标识语义（ADR-002 + ADR-050，本 VO 的两个 ID 不可互换）</b>：
 * <ul>
 *   <li>{@link #id} —— 持券的<b>技术主键</b> {@code user_coupon.id}，即契约的
 *       {@code user_coupon_id}（下单时回传 {@code CreateOrderReq.user_coupon_id}）；</li>
 *   <li>{@link #couponId} —— 券的<b>业务唯一标识</b>（券号）：{@code coupon.coupon_id}
 *       的对外字符串形态（32 位小写 hex，由 {@code BINARY(16)} 经
 *       {@code IdGenerator.toHexString} 产出），与 {@code CouponVO.couponId}
 *       <b>同型同义</b> —— 前端因此可按 {@code coupon_id} 对
 *       {@code GET /api/coupon/available} 与 {@code GET /api/coupon/template} 做本地 join。</li>
 * </ul>
 *
 * <p><b>禁止暴露技术字段</b>：{@code user_coupon.coupon_id}（BIGINT）是<b>技术外键</b>
 * （指向 {@code coupon.id}），只允许存在于 PO 与领域层，<b>不得出现在任何 VO/DTO</b>；
 * 把技术主键写成券号会导致前端 join 永远匹配不上（同名不同型不同义）。
 *
 * <p>fail-open 口径：券模板已删除 / 券面读模型异常 → {@link #couponId} 为 {@code null}
 * （与券面字段、{@link #discountType} 同口径），持券行照常返回、不缩行。
 *
 * <p><b>不投影 {@code coupon.type}</b>：该列自 V4.0 起 DEPRECATED，明确不纳入契约
 * （契约 §4.10.1；折扣类型唯一口径为 {@link #discountType}）。
 */
@Data
public class UserCouponVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 持券技术主键（{@code user_coupon.id}）= 契约的 {@code user_coupon_id}。 */
    private Long id;

    /** 券业务唯一标识（券号）：{@code coupon.coupon_id} 的 32 位小写 hex 形态。 */
    private String couponId;

    private Integer status;
    private String usedOrderNo;
    private LocalDateTime useTime;
    private LocalDateTime createTime;

    // ==================== 券面展示字段（ADR-057 回填） ====================

    /** 券名称（{@code coupon.name}）；模板已删除时为 null */
    private String name;

    /** 券面值（分；满减=减免金额，折扣=百分比口径，含义由 {@link #discountType} 决定） */
    private Integer value;

    /** 最低消费金额（分） */
    private Integer minAmount;

    /**
     * 优惠类型（{@code coupon.discount_type}，契约 §4.10.1；线上键 {@code discount_type}）：
     * {@code 1}=FIXED 满减（{@link #value} 为<b>分</b>）/ {@code 2}=PERCENTAGE 折扣
     * （{@link #value} 为<b>百分比</b>，{@code 85} 表 8.5 折）/ {@code 3}=CASH_COUPON 代金券。
     *
     * <p><b>前端据此区分面额渲染</b>：折扣券按 {@code -¥value} 渲染是错的。
     * 取值链路上无任何回落（取不到即 {@code null}），与券面其余字段同口径。
     */
    private Integer discountType;
}
