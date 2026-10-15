package org.icedamericanomall.user.infrastructure.persistence.favorite;

import org.h2.Driver;
import org.icedamericanomall.user.app.dto.ProductBriefView;
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
 * ⑤空入参/未知 ID 不报错。
 */
@DisplayName("收藏商品摘要读模型 — H2 SQL 语义")
class FavoriteProductBriefQueryImplH2Test {

    private FavoriteProductBriefQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:favorite_brief_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE product ("
                    + "id BIGINT PRIMARY KEY, name VARCHAR(128), main_image VARCHAR(255), status INT)");
            stmt.execute("CREATE TABLE sku ("
                    + "id BIGINT PRIMARY KEY, product_id BIGINT, price INT, status INT)");
            stmt.execute("INSERT INTO product VALUES "
                    + "(1001, '手工陶瓷茶杯', 'https://cdn.icedmall.com/p/1001.jpg', 1),"
                    + "(1002, '已下架的商品', 'https://cdn.icedmall.com/p/1002.jpg', 2),"
                    + "(1003, '无在售SKU的商品', NULL, 1)");
            stmt.execute("INSERT INTO sku VALUES "
                    + "(11, 1001, 9900, 2),"   // 下架 SKU：不参与最低价
                    + "(12, 1001, 12800, 1),"
                    + "(13, 1001, 15800, 1),"
                    + "(14, 1002, 5000, 1),"
                    + "(15, 1003, 7000, 2)");
        }
        query = new FavoriteProductBriefQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findByIds — 名称/主图/状态逐列映射，价格取在售 SKU 最低价")
    void shouldMapBriefColumnsAndLowestOnSalePrice() {
        Map<Long, ProductBriefView> briefs = query.findByIds(List.of(1001L));

        ProductBriefView brief = briefs.get(1001L);
        assertEquals("手工陶瓷茶杯", brief.productName());
        assertEquals("https://cdn.icedmall.com/p/1001.jpg", brief.mainImage());
        assertEquals(1, brief.status());
        assertEquals(12800, brief.price(), "应取在售 SKU 最低价（9900 的下架 SKU 不参与）");
    }

    @Test
    @DisplayName("findByIds — 下架商品仍返回行（前端据 status 渲染失效态），价格照常取在售最低价")
    void shouldReturnOffShelfProduct_whenStatusIsOffShelf() {
        Map<Long, ProductBriefView> briefs = query.findByIds(List.of(1002L));

        assertEquals(2, briefs.get(1002L).status());
        assertEquals(5000, briefs.get(1002L).price());
    }

    @Test
    @DisplayName("findByIds — 无在售 SKU → 价格为 null（不回落为 0）")
    void shouldReturnNullPrice_whenNoOnSaleSku() {
        Map<Long, ProductBriefView> briefs = query.findByIds(List.of(1003L));

        assertTrue(briefs.containsKey(1003L));
        assertNull(briefs.get(1003L).price());
        assertNull(briefs.get(1003L).mainImage());
    }

    @Test
    @DisplayName("findByIds — 未知商品 ID 不出现在结果中（调用方按缺失置空字段）")
    void shouldOmitUnknownProductId() {
        Map<Long, ProductBriefView> briefs = query.findByIds(List.of(1001L, 9999L));

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
}
