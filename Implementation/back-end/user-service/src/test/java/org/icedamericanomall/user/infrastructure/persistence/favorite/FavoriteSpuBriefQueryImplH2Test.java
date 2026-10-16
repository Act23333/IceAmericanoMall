package org.icedamericanomall.user.infrastructure.persistence.favorite;

import org.h2.Driver;
import org.icedamericanomall.user.app.dto.SpuBriefView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 收藏商品摘要读模型 H2 集成测试 —— 锁死联表 SQL 语义（mock 无法覆盖的部分）。
 *
 * <p>验证：①主图/名称/状态逐列映射；②价格取<b>在售</b> SKU 最低价（下架 SKU 不参与）；
 * ③无在售 SKU → 价格为 null；④下架商品仍返回行（前端据 status 渲染失效态）；
 * ⑤空入参/未知 ID 不报错；⑥<b>业务键口径</b>（{@code spu.spu_id BINARY(16)}，ADR-061）
 * 可按 32-hex 业务键命中，且键/值与 {@code toHexString} 同源（大小写归一）。
 *
 * <p>DDL 与生产结构对齐（{@code spu.spu_id BINARY(16)} / {@code sku.spu_id} 技术外键），
 * 否则 SQL 里的业务键列在 H2 上不可见（列缺失会把真实缺陷伪装成语法错误）。
 */
@DisplayName("收藏商品摘要读模型 — H2 SQL 语义")
class FavoriteSpuBriefQueryImplH2Test {

    /** 商品业务键（32-hex，{@code spu.spu_id} BINARY(16)）。 */
    private static final String SPU_BIZ_1001 = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String SPU_BIZ_1002 = "112233445566778899aabbccddeeff00";
    private static final String SPU_BIZ_1003 = "aabbccddeeff00112233445566778899";

    private FavoriteSpuBriefQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:favorite_brief_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE spu ("
                    + "id BIGINT PRIMARY KEY, spu_id BINARY(16) NOT NULL UNIQUE, "
                    + "name VARCHAR(128), main_image VARCHAR(255), status INT)");
            // sku.spu_id 是商品【技术外键】（A 案：同库内部 FK 不动，ADR-061）
            stmt.execute("CREATE TABLE sku ("
                    + "id BIGINT PRIMARY KEY, spu_id BIGINT, price INT, status INT)");
            stmt.execute("INSERT INTO spu (id, spu_id, name, main_image, status) VALUES "
                    + "(1001, X'" + SPU_BIZ_1001 + "', '手工陶瓷茶杯', 'https://cdn.icedmall.com/p/1001.jpg', 1),"
                    + "(1002, X'" + SPU_BIZ_1002 + "', '已下架的商品', 'https://cdn.icedmall.com/p/1002.jpg', 2),"
                    + "(1003, X'" + SPU_BIZ_1003 + "', '无在售SKU的商品', NULL, 1)");
            stmt.execute("INSERT INTO sku VALUES "
                    + "(11, 1001, 9900, 2),"   // 下架 SKU：不参与最低价
                    + "(12, 1001, 12800, 1),"
                    + "(13, 1001, 15800, 1),"
                    + "(14, 1002, 5000, 1),"
                    + "(15, 1003, 7000, 2)");
        }
        query = new FavoriteSpuBriefQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findByIds — 名称/主图/状态逐列映射，价格取在售 SKU 最低价")
    void shouldMapBriefColumnsAndLowestOnSalePrice() {
        Map<Long, SpuBriefView> briefs = query.findByIds(List.of(1001L));

        SpuBriefView brief = briefs.get(1001L);
        assertEquals("手工陶瓷茶杯", brief.productName());
        assertEquals("https://cdn.icedmall.com/p/1001.jpg", brief.mainImage());
        assertEquals(1, brief.status());
        assertEquals(12800, brief.price(), "应取在售 SKU 最低价（9900 的下架 SKU 不参与）");
    }

    @Test
    @DisplayName("findByIds — 下架商品仍返回行（前端据 status 渲染失效态），价格照常取在售最低价")
    void shouldReturnOffShelfSpu_whenStatusIsOffShelf() {
        Map<Long, SpuBriefView> briefs = query.findByIds(List.of(1002L));

        assertEquals(2, briefs.get(1002L).status());
        assertEquals(5000, briefs.get(1002L).price());
    }

    @Test
    @DisplayName("findByIds — 无在售 SKU → 价格为 null（不回落为 0）")
    void shouldReturnNullPrice_whenNoOnSaleSku() {
        Map<Long, SpuBriefView> briefs = query.findByIds(List.of(1003L));

        assertTrue(briefs.containsKey(1003L));
        assertNull(briefs.get(1003L).price());
        assertNull(briefs.get(1003L).mainImage());
    }

    @Test
    @DisplayName("findByIds — 未知商品 ID 不出现在结果中（调用方按缺失置空字段）")
    void shouldOmitUnknownSpuId() {
        Map<Long, SpuBriefView> briefs = query.findByIds(List.of(1001L, 9999L));

        assertTrue(briefs.containsKey(1001L));
        assertFalse(briefs.containsKey(9999L));
    }

    @Test
    @DisplayName("findByIds — 空集合/全 null 入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenIdsBlank() {
        assertTrue(query.findByIds(List.of()).isEmpty());
        assertTrue(query.findByIds(null).isEmpty());
        assertTrue(query.findByIds(java.util.Arrays.asList(null, null)).isEmpty());
    }

    // ==================== 业务键口径（ADR-061 §决策 2） ====================

    @Test
    @DisplayName("findByBusinessIds — 按 32-hex 业务键命中，键/值与 BINARY(16) 列同源")
    void shouldFindByBusinessKey() {
        Map<String, SpuBriefView> briefs =
                query.findByBusinessIds(List.of(SPU_BIZ_1001, SPU_BIZ_1003));

        assertEquals(2, briefs.size());
        assertEquals("手工陶瓷茶杯", briefs.get(SPU_BIZ_1001).productName());
        assertEquals(1001L, briefs.get(SPU_BIZ_1001).spuId());
        assertEquals(SPU_BIZ_1001, briefs.get(SPU_BIZ_1001).spuBusinessId());
        assertEquals(12800, briefs.get(SPU_BIZ_1001).price());
        // 无在售 SKU 的商品同样按业务键命中（价格为 null，不回落为 0）
        assertTrue(briefs.containsKey(SPU_BIZ_1003));
        assertNull(briefs.get(SPU_BIZ_1003).price());
    }

    @Test
    @DisplayName("findByBusinessIds — 非法/未知业务键一律「未命中」，不放大成异常")
    void shouldOmitUnknownOrIllegalBusinessKey() {
        Map<String, SpuBriefView> briefs = query.findByBusinessIds(
                List.of(SPU_BIZ_1002, "not-a-hex-key", "0f1e2d3c4b5a69788796a5b4c3d2e1f1"));

        assertEquals(1, briefs.size());
        assertTrue(briefs.containsKey(SPU_BIZ_1002));
        assertTrue(query.findByBusinessIds(List.of()).isEmpty());
        assertTrue(query.findByBusinessIds(null).isEmpty());
    }
}
