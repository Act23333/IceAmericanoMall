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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 券面读模型 H2 集成测试 —— 锁死 {@code coupon} 表列口径（mock 无法覆盖的部分）。
 *
 * <p>契约 §4.10.1 {@code UserCouponVO} 声明的券面三字段此前未投影，前端只能用
 * {@code GET /api/coupon/available} + {@code GET /api/coupon/template} 本地 join。
 *
 * <p>覆盖：①{@code coupon_id}/{@code name}/{@code value}/{@code min_amount} 逐列映射，
 * 其中 {@code coupon_id} 是 {@code BINARY(16)} → <b>32 位小写 hex 业务券号</b>（ADR-050 转换口径，
 * 复用 {@code IdGenerator.toHexString}）；②{@code min_amount=0}（无门槛券）照常返回 0，不回落为
 * null；③模板已删除 → 该键不出现（调用方按缺失留空字段，持券行照常返回）；④部分命中时命中行
 * 不缩、不降级；⑤空集合 / 全 null 入参不发查询且返回空 Map。
 */
@DisplayName("券面读模型 — H2 SQL 语义")
class CouponFaceQueryImplH2Test {

    /** 命中模板的业务券号（32-hex；与 H2 建表插入的 {@code X'...'} 字面量一致）。 */
    private static final String COUPON_NO_HIT = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 无门槛券的业务券号。 */
    private static final String COUPON_NO_FREE = "1a2b3c4d5e6f708192a3b4c5d6e7f809";

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
                    + "id BIGINT PRIMARY KEY, coupon_id BINARY(16), "
                    + "name VARCHAR(100), `value` INT, min_amount INT)");
            stmt.execute("INSERT INTO coupon (id, coupon_id, name, `value`, min_amount) VALUES "
                    + "(1, X'" + COUPON_NO_HIT + "', '618满200减30', 3000, 20000),"
                    + "(2, X'" + COUPON_NO_FREE + "', '无门槛5元券', 500, 0)");
        }
        query = new CouponFaceQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findByCouponIds — 业务券号/券名/券面值/最低消费逐列映射")
    void shouldMapFaceColumns() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L));

        CouponFace face = faces.get(1L);
        assertEquals(COUPON_NO_HIT, face.couponId(), "coupon.coupon_id(BINARY16) → 32-hex 业务券号");
        assertEquals("618满200减30", face.name(), "coupon.name → name");
        assertEquals(3000, face.value(), "coupon.value → value（分）");
        assertEquals(20000, face.minAmount(), "coupon.min_amount → min_amount（分）");
        assertEquals(1L, face.id(), "键口径仍是券模板技术主键 coupon.id（内部字段）");
    }

    @Test
    @DisplayName("findByCouponIds — coupon_id 解码为 32 位小写 hex，且不是技术主键")
    void shouldDecodeCouponIdAs32Hex() {
        CouponFace face = query.findByCouponIds(List.of(1L)).get(1L);

        assertTrue(face.couponId().matches("[0-9a-f]{32}"),
                "业务券号形态必须是 32 位小写 hex，实际: " + face.couponId());
        assertEquals(32, face.couponId().length());
        assertNotEquals(String.valueOf(face.id()), face.couponId(),
                "技术主键（coupon.id）不得被当作业务券号（BINARY(16) 列未被解码的典型症状）");
    }

    @Test
    @DisplayName("findByCouponIds — 无门槛券 min_amount=0 照常返回 0（不回落为 null）")
    void shouldKeepZeroMinAmount_whenNoThreshold() {
        CouponFace face = query.findByCouponIds(List.of(2L)).get(2L);

        assertEquals(0, face.minAmount(), "0 是合法门槛（无门槛券），不能被当作缺失");
        assertEquals(500, face.value());
        assertEquals(COUPON_NO_FREE, face.couponId());
    }

    @Test
    @DisplayName("findByCouponIds — 模板已删除 → 键不出现（调用方按缺失留空字段）")
    void shouldOmitUnknownCouponId() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L, 999L));

        assertEquals(1, faces.size());
        assertTrue(faces.containsKey(1L));
        assertNull(faces.get(999L));
    }

    @Test
    @DisplayName("findByCouponIds — 部分命中：命中行不缩、业务券号照常解码")
    void shouldKeepHitRowIntact_whenPartiallyMissing() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L, 999L));

        assertEquals(1, faces.size(), "缺失模板不得让命中行消失/降级");
        CouponFace hit = faces.get(1L);
        assertEquals(COUPON_NO_HIT, hit.couponId());
        assertTrue(hit.couponId().matches("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("findByCouponIds — 重复 ID 去重，单条查询返回单行")
    void shouldDeduplicateIds() {
        Map<Long, CouponFace> faces = query.findByCouponIds(List.of(1L, 1L, 1L));

        assertEquals(1, faces.size());
        assertEquals("618满200减30", faces.get(1L).name());
        assertEquals(COUPON_NO_HIT, faces.get(1L).couponId());
    }

    @Test
    @DisplayName("findByCouponIds — 空集合 / 全 null 入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenIdsBlank() {
        assertTrue(query.findByCouponIds(List.of()).isEmpty());
        assertTrue(query.findByCouponIds(null).isEmpty());
        assertTrue(query.findByCouponIds(Arrays.asList(null, null)).isEmpty());
    }
}
