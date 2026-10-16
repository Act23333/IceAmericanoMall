package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import org.h2.Driver;
import org.icedamericanomall.marketing.app.dto.FlashSaleSpuBrief;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀商品摘要读模型 H2 集成测试 —— 锁死联表 SQL 语义（mock 无法覆盖的部分）。
 *
 * <p>验证：①商品名/主图/划线原价逐列映射；②未设划线价 → {@code originalPrice} 为 null
 * （不回落为售价）；③商品已物理删除的 SKU 不出现（调用方按缺失置空字段）；
 * ④空入参不发查询。
 */
@DisplayName("秒杀商品摘要读模型 — H2 SQL 语义")
class FlashSaleSpuBriefQueryImplH2Test {

    private FlashSaleSpuBriefQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:flash_brief_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE spu ("
                    + "id BIGINT PRIMARY KEY, name VARCHAR(128), main_image VARCHAR(255), status INT)");
            stmt.execute("CREATE TABLE sku ("
                    + "id BIGINT PRIMARY KEY, spu_id BIGINT, price INT, original_price INT, status INT)");
            stmt.execute("INSERT INTO spu VALUES "
                    + "(100, '手工陶瓷茶杯', 'https://cdn.icedmall.com/product/main_100.jpg', 1)");
            stmt.execute("INSERT INTO sku VALUES "
                    + "(10, 100, 9900, 12800, 1),"     // 正常：有划线价
                    + "(11, 100, 9900, NULL, 1),"      // 未设划线价
                    + "(12, 999, 5000, 6000, 1)");     // 商品已物理删除（孤儿 SKU）
        }
        query = new FlashSaleSpuBriefQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findBySkuIds — 商品名/主图/划线原价逐列映射")
    void shouldMapBriefColumns() {
        Map<Long, FlashSaleSpuBrief> briefs = query.findBySkuIds(List.of(10L));

        FlashSaleSpuBrief brief = briefs.get(10L);
        assertEquals("手工陶瓷茶杯", brief.productName());
        assertEquals("https://cdn.icedmall.com/product/main_100.jpg", brief.productImage());
        assertEquals(12800, brief.originalPrice());
    }

    @Test
    @DisplayName("findBySkuIds — 未设划线价 → originalPrice 为 null（不回落为售价）")
    void shouldReturnNullOriginalPrice_whenSkuHasNoOriginalPrice() {
        Map<Long, FlashSaleSpuBrief> briefs = query.findBySkuIds(List.of(11L));

        assertTrue(briefs.containsKey(11L));
        assertNull(briefs.get(11L).originalPrice());
        assertEquals("手工陶瓷茶杯", briefs.get(11L).productName());
    }

    @Test
    @DisplayName("findBySkuIds — 商品已删除的 SKU 不出现（调用方按缺失置空字段）")
    void shouldOmitSku_whenSpuGone() {
        Map<Long, FlashSaleSpuBrief> briefs = query.findBySkuIds(List.of(10L, 12L));

        assertTrue(briefs.containsKey(10L));
        assertFalse(briefs.containsKey(12L));
    }

    @Test
    @DisplayName("findBySkuIds — 空集合/全 null 入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenIdsBlank() {
        assertTrue(query.findBySkuIds(List.of()).isEmpty());
        assertTrue(query.findBySkuIds(null).isEmpty());
        assertTrue(query.findBySkuIds(Arrays.asList(null, null)).isEmpty());
    }
}
