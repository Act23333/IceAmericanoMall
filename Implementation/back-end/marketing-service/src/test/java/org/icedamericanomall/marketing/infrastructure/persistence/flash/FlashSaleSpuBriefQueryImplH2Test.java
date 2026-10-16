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
    /** 行 10 的 SKU 业务键（H2 BINARY(16) 小写 hex 形态）与所属 SPU 业务键。 */
    private static final String SKU_BUSINESS_ID = "0000000000000000000000000000000a";
    private static final String SPU_BUSINESS_ID = "00000000000000000000000000000064";


    private FlashSaleSpuBriefQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:flash_brief_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // 业务键列按 Initialize.sql 形态建表（BINARY(16) + 唯一索引）——两种入参口径都被覆盖
            stmt.execute("CREATE TABLE spu ("
                    + "id BIGINT PRIMARY KEY, spu_id BINARY(16), name VARCHAR(128), "
                    + "main_image VARCHAR(255), status INT, CONSTRAINT uk_spu_id UNIQUE (spu_id))");
            stmt.execute("CREATE TABLE sku ("
                    + "id BIGINT PRIMARY KEY, sku_id BINARY(16), spu_id BIGINT, price INT, "
                    + "original_price INT, status INT, CONSTRAINT uk_sku_id UNIQUE (sku_id))");
            stmt.execute("INSERT INTO spu VALUES "
                    + "(100, X'00000000000000000000000000000064', '手工陶瓷茶杯', "
                    + "'https://cdn.icedmall.com/product/main_100.jpg', 1)");
            stmt.execute("INSERT INTO sku VALUES "
                    + "(10, X'0000000000000000000000000000000A', 100, 9900, 12800, 1),"  // 正常：有划线价
                    + "(11, X'0000000000000000000000000000000B', 100, 9900, NULL, 1),"   // 未设划线价
                    + "(12, X'0000000000000000000000000000000C', 999, 5000, 6000, 1)");  // 商品已物理删除（孤儿 SKU）
        }
        query = new FlashSaleSpuBriefQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findBySkuIds — 商品名/主图/划线原价逐列映射")
    void shouldMapBriefColumns() {
        Map<Long, FlashSaleSpuBrief> briefs = query.findBySkuIds(List.of(10L));

        FlashSaleSpuBrief brief = briefs.get(10L);
        assertEquals("手工陶瓷茶杯", brief.spuName());
        assertEquals("https://cdn.icedmall.com/product/main_100.jpg", brief.mainImage());
        assertEquals(12800, brief.originalPrice());
    }

    @Test
    @DisplayName("findBySkuIds — 未设划线价 → originalPrice 为 null（不回落为售价）")
    void shouldReturnNullOriginalPrice_whenSkuHasNoOriginalPrice() {
        Map<Long, FlashSaleSpuBrief> briefs = query.findBySkuIds(List.of(11L));

        assertTrue(briefs.containsKey(11L));
        assertNull(briefs.get(11L).originalPrice());
        assertEquals("手工陶瓷茶杯", briefs.get(11L).spuName());
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

    @Test
    @DisplayName("findBySkuBusinessIds — 业务键入参：键为业务键，并带出 SPU 业务键（ADR-061）")
    void shouldResolveByBusinessKey_andCarrySpuBusinessId() {
        Map<String, FlashSaleSpuBrief> briefs = query.findBySkuBusinessIds(List.of(SKU_BUSINESS_ID));

        assertEquals(1, briefs.size());
        FlashSaleSpuBrief brief = briefs.get(SKU_BUSINESS_ID);
        assertEquals(10L, brief.skuId(), "技术键仍随行带出（写路径解析用）");
        assertEquals(100L, brief.spuId(), "SPU 技术键（写路径解析用）");
        assertEquals(SPU_BUSINESS_ID, brief.spuBusinessId(), "SPU 业务键 ← spu.spu_id（32-hex）");
        assertEquals(SKU_BUSINESS_ID, brief.skuBusinessId());
    }

    @Test
    @DisplayName("findBySkuBusinessIds — 非法/不存在业务键不出现（不抛异常、不放大成 500）")
    void shouldOmitUnknownBusinessKey() {
        Map<String, FlashSaleSpuBrief> briefs =
                query.findBySkuBusinessIds(List.of(SKU_BUSINESS_ID, "prod-001"));

        assertEquals(1, briefs.size());
        assertTrue(briefs.containsKey(SKU_BUSINESS_ID));
    }
}
