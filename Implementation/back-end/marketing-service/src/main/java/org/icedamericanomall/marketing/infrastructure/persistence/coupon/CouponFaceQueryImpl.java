package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.app.dto.CouponFace;
import org.icedamericanomall.marketing.app.port.CouponFaceQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 优惠券券面读模型查询实现（基础设施层）—— 只读单表读模型（单条 SQL）。
 *
 * <p>字段口径（契约 §4.10.1 {@code UserCouponVO}）：{@code name} ← {@code coupon.name}；
 * {@code value} ← {@code coupon.value}；{@code min_amount} ← {@code coupon.min_amount}。
 * 键口径：{@code coupon.id}（= {@code user_coupon.coupon_id} 指向的模板主键）。
 *
 * <p><b>只读</b>：本类仅 SELECT，不写任何表。参数一律走占位符（{@code IN (?,?)}），
 * 不自拼字面量（ADR-050 SQL 字面量禁令）。
 */
@Repository
@RequiredArgsConstructor
public class CouponFaceQueryImpl implements CouponFaceQuery {

    private static final String SQL_SELECT_FACE =
            "SELECT c.id AS coupon_id, c.name AS name, c.value AS value, c.min_amount AS min_amount "
                    + "FROM coupon c WHERE c.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<Long, CouponFace> findByCouponIds(Collection<Long> couponIds) {
        List<Long> ids = normalize(couponIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<CouponFace> rows = jdbcTemplate.query(
                SQL_SELECT_FACE.formatted(placeholders),
                (rs, rowNum) -> new CouponFace(
                        rs.getObject("coupon_id", Long.class),
                        rs.getString("name"),
                        rs.getObject("value", Integer.class),
                        rs.getObject("min_amount", Integer.class)),
                ids.toArray());
        return rows.stream()
                .filter(row -> row.couponId() != null)
                .collect(Collectors.toMap(CouponFace::couponId, row -> row, (first, ignored) -> first));
    }

    private List<Long> normalize(Collection<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return List.of();
        }
        return couponIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
