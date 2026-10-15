package org.icedamericanomall.marketing.app.dto;

/**
 * 优惠券券面信息（应用层读模型，不可变）。
 *
 * <p><b>存在理由</b>：{@code user_coupon} 只存 {@code coupon_id}（券模板<b>技术外键</b>），而契约
 * （{@code contract/05-API-Specification.md} §4.10.1）声明的 {@code UserCouponVO} 要求
 * 投影券面 {@code name} / {@code value} / {@code min_amount} <b>以及业务券号 {@code coupon_id}</b>
 * —— 前端因此不得不再打一次 {@code GET /api/coupon/template} 在本地 join（双端口径分叉）。
 * 本记录即这四列的载体，由 {@code CouponFaceQuery} 按 <b>券模板 ID 批量</b>装配
 * （整页一次查询，无 N+1）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * <p><b>两个标识不可互换（ADR-002 + ADR-050）</b>：{@link #id} 是券模板的技术主键
 * （{@code coupon.id}，也是本读模型 Map 的键）；{@link #couponId} 是业务券号
 * （{@code coupon.coupon_id} 的 32 位小写 hex 对外形态）。命名与领域聚合
 * {@code domain.model.coupon.Coupon}（{@code id} 技术 / {@code couponId} 业务）保持同一口径，
 * 避免「同名不同义」再次发生。
 *
 * @param id        券模板技术主键（{@code coupon.id}，与 {@code user_coupon.coupon_id} 对齐）；
 *                  <b>内部字段</b>，不得出现在任何 VO/DTO
 * @param couponId  券业务唯一标识（券号）：{@code coupon.coupon_id}（{@code BINARY(16)}）经
 *                  {@code IdGenerator.toHexString} 解出的 32 位小写 hex；对外唯一口径
 * @param name      券名称（{@code coupon.name}）
 * @param value     券面值（分；满减券为减免金额，折扣券为百分比口径，见 {@code discount_type}）
 * @param minAmount 最低消费金额（分）
 */
public record CouponFace(
        Long id,
        String couponId,
        String name,
        Integer value,
        Integer minAmount) {
}
