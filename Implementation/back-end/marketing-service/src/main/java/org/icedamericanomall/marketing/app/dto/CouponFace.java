package org.icedamericanomall.marketing.app.dto;

/**
 * 优惠券券面信息（应用层读模型，不可变）。
 *
 * <p><b>存在理由</b>：{@code user_coupon} 只存 {@code coupon_id}（券模板主键），而契约
 * （{@code contract/05-API-Specification.md} §4.10.1）声明的 {@code UserCouponVO} 要求
 * 投影券面 {@code name} / {@code value} / {@code min_amount} —— 前端因此不得不再打一次
 * {@code GET /api/coupon/template} 在本地 join（双端口径分叉）。本记录即这三列的载体，
 * 由 {@code CouponFaceQuery} 按 <b>券模板 ID 批量</b>装配（整页一次查询，无 N+1）。
 *
 * <p>刻意不用 {@code DTO}/{@code CO} 后缀：它不跨进程、不出现在任何线格式契约中。
 *
 * @param couponId  券模板主键（{@code coupon.id}，与 {@code user_coupon.coupon_id} 对齐）
 * @param name      券名称（{@code coupon.name}）
 * @param value     券面值（分；满减券为减免金额，折扣券为百分比口径，见 {@code discount_type}）
 * @param minAmount 最低消费金额（分）
 */
public record CouponFace(
        Long couponId,
        String name,
        Integer value,
        Integer minAmount) {
}
