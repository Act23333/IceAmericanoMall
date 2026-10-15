package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import org.h2.Driver;
import org.icedamericanomall.marketing.app.dto.CouponFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券面读模型 H2 集成测试 —— 锁死 {@code coupon} 表列口径（mock 无法覆盖的部分）。
 *
 * <p>契约 §4.10.1 {@code UserCouponVO} 声明的券面三字段此前未投影，前端只能用
 * {@code GET /api/coupon/available} + {@code GET /api/coupon/template} 本地 join。
 *
 * <p>覆盖：①{@code name}/{@code value}/{@code min_amount} 逐列映射；②{@code min_amount=0}
 * （无门槛券）照常返回 0，不回落为 null；③模板已删除 → 该键不出现（调用方按缺失留空三字段，
 * 持券行照常返回）；④空集合 / 全 null 入参不发查询且返回空 Map。
 */
@DisplayName("券面读模型 — H2 SQL 语义")
class CouponFaceQueryImplH2Test {

    private CouponFaceQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:coupon_face_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coupon ("
                    + "id BIGINT PRIMARY KEY, name VARCHAR(100), `value` INT, min_amount INT)");
            stmt.execute("INSERT INTO coupon VALUES "
                    + "(1, '618满200减30', 3000, 20000),"
                    + "(2, '无门槛5元券', 500, 0)");
        }
        query = new CouponFaceQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findByCouponIds — 券名/券面值/最低消费逐列映射")
    void shouldMapFaceColumns() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L));

        CouponFace face = faces.get(1L);
        assertEquals("618满200减30", face.name(), "coupon.name → name");
        assertEquals(3000, face.value(), "coupon.value → value（分）");
        assertEquals(20000, face.minAmount(), "coupon.min_amount → min_amount（分）");
        assertEquals(1L, face.couponId());
    }

    @Test
    @DisplayName("findByCouponIds — 无门槛券 min_amount=0 照常返回 0（不回落为 null）")
    void shouldKeepZeroMinAmount_whenNoThreshold() {
        CouponFace face = query.findByCouponIds(List.of(2L)).get(2L);

        assertEquals(0, face.minAmount(), "0 是合法门槛（无门槛券），不能被当作缺失");
        assertEquals(500, face.value());
    }

    @Test
    @DisplayName("findByCouponIds — 模板已删除 → 键不出现（调用方按缺失留空三字段）")
    void shouldOmitUnknownCouponId() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L, 999L));

        assertEquals(1, faces.size());
        assertTrue(faces.containsKey(1L));
        assertNull(faces.get(999L));
    }

    @Test
    @DisplayName("findByCouponIds — 重复 ID 去重，单条查询返回单行")
    void shouldDeduplicateIds() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L, 1L, 1L));

        assertEquals(1, faces.size());
        assertEquals("618满200减30", faces.get(1L).name());
    }

    @Test
    @DisplayName("findByCouponIds — 空集合 / 全 null 入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenIdsBlank() {
        assertTrue(query.findByCouponIds(List.of()).isEmpty());
        assertTrue(query.findByCouponIds(null).isEmpty());
        assertTrue(query.findByCouponIds(Arrays.asList(null, null)).isEmpty());
    }
}
