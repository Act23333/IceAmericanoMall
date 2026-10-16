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
 *
 * <p><b>适用范围投影（🆕 本批补齐，B1 遗留）</b>：{@link #scopeType}/{@link #scopeValues}
 * 为契约 §4.10.1 已声明、实现缺失的两个字段（「文档有代码无」）；取值由同一条券面读模型
 * SQL 顺带带出（零额外往返），{@code scope_values} 对外归一为 <b>CSV</b>。
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

    /**
     * 适用范围类型（{@code coupon.scope_type}，契约 §4.10.1；B1 遗留投影，本批补齐）：
     * {@code 1}=ALL 全场 / {@code 2}=CATEGORY 限定品类 / {@code 3}=PRODUCT 限定单品。
     *
     * <p><b>缺省语义</b>：{@code null} 按 {@code 1}（ALL）消费 —— 存量数据/读模型降级都可能是 null。
     */
    private Integer scopeType;

    /**
     * 适用范围取值（对外 <b>CSV 串</b>；与 {@code CouponVO.scopeValues} 同型同义）。
     *
     * <p>存储形态是 DB {@code JSON} 数组（唯一真相源），由服务端归一为逗号分隔串
     * （值内不含分隔符，故无歧义）；{@code scope_type} 为 {@code null}/1（ALL）时恒为 {@code null}。
     * {@code scope_type=3} 时为 <b>SPU 业务键</b> 32-hex（ADR-061：与线格式 {@code spu_id} 同型，
     * 客户端预过滤因此可命中）。
     *
     * <p><b>fail-open</b>：券模板已删除 / 读模型降级 → 与 {@link #scopeType} <b>同为 null</b>
     * （不回落为 1、不回落为空串）。<b>前端必须把 {@code scope_type=null} 按 ALL 消费</b>。
     */
    private String scopeValues;
}
