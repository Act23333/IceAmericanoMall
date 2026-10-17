package org.icedamericanomall.marketing.adapter.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 我的优惠券三态计数视图对象（M7 {@code GET /api/coupon/count} 出参）。
 *
 * <p><b>批次 D</b>：改造前返回 {@code Result<Map<String, Integer>>}，键由
 * {@code CouponUsageManager.countMyCoupons} 以 {@code LinkedHashMap.put("available", …)} 拼装
 * （同 {@code PointsBalanceVO} 的 V5.4 弱类型收口先例，本轮补齐剩余同类返回）。
 * 现改为强类型 VO，<b>计数口径与数值未变</b>（纯类型重构）。
 *
 * <p><b>线上键（无 BREAKING）</b>：三个字段都是单词，{@code SNAKE_CASE} 下线上键与改造前
 * Map 的字面键<b>逐字节相同</b> —— {@code available} / {@code used} / {@code expired}。
 *
 * <p><b>口径</b>：与 {@code GET /api/coupon/available}、{@code /used} 的列表查询同源
 * （{@code user_coupon.status} 落库态，{@code UserCouponStatusEnum}）——
 * {@code available}=未使用(1) / {@code used}=已使用(2) / {@code expired}=已过期(3)，
 * 三者互斥且穷尽，故 {@code available + used + expired} = 该用户持券总行数。
 * 计数为 <b>0 也是合法业务态</b>（无持券），不得据此报错或回 {@code null}。
 */
@Data
public class UserCouponCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未使用券数（{@code user_coupon.status = 1}，单位：张）。 */
    private Integer available;

    /** 已使用券数（{@code user_coupon.status = 2}，单位：张）。 */
    private Integer used;

    /** 已过期券数（{@code user_coupon.status = 3}，单位：张）。 */
    private Integer expired;

    /**
     * 装配入口（三个标量 → VO；字段与既有 Map 键逐一对应）。
     *
     * @param available 未使用券数（非负）
     * @param used      已使用券数（非负）
     * @param expired   已过期券数（非负）
     * @return 三态计数视图对象
     */
    public static UserCouponCountVO of(int available, int used, int expired) {
        UserCouponCountVO vo = new UserCouponCountVO();
        vo.setAvailable(available);
        vo.setUsed(used);
        vo.setExpired(expired);
        return vo;
    }
}
