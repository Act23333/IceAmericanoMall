package org.icedamericanomall.item.infrastructure.persistence;

import org.h2.Driver;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
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
 * SKU 商品展示事实读模型 H2 集成测试 —— 锁死 I13 联表 SQL 的<b>列口径</b>
 * （mock 完全覆盖不到的部分）。
 *
 * <p><b>为什么必须有这层测试</b>：原缺陷（购物车 {@code product_name} 恒空）之所以能躲过
 * 全部单测，正是因为上层测试 mock 了 {@code SkuCO} —— 没人校验 SQL 是否真的取到了
 * {@code spu} 表的列。本测试用真实 H2 表 + 真实 SQL 逐列断言。
 *
 * <p>覆盖：①四列逐列映射（含别名）；②商品行缺失 → 键存在但四字段为 null（LEFT JOIN 不吞行）；
 * ③未绑定运费模板 → 该列 null 而其余三列照常有值；④重复 ID 去重（占位符与结果均不重复）；
 * ⑤空集合 / 全 null 入参不发查询且返回空 Map。
 */
@DisplayName("SKU 商品展示事实读模型 — H2 SQL 语义（I13 列口径）")
class SkuSpuDisplayQueryImplH2Test {

    /** 行 11 的 SKU 业务键（H2 的 BINARY(16) 小写 hex 形态）。 */
    private static final String SKU_BUSINESS_ID = "00000000000000000000000000000011";
    /** 行 11 所属 SPU 的业务键。 */
    private static final String SPU_BUSINESS_ID = "000000000000000000000000000000a1";

    private SkuSpuDisplayQueryImpl query;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:sku_display_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // 业务键列按 Initialize.sql 形态建表（BINARY(16) + 唯一索引）——本测试同时覆盖
            // 「按技术键」与「按业务键」两种入参口径（ADR-061 §决策 2）
            stmt.execute("CREATE TABLE spu ("
                    + "id BIGINT PRIMARY KEY, spu_id BINARY(16), name VARCHAR(200), seller_id BIGINT, "
                    + "category_id BIGINT, freight_template_id BIGINT, "
                    + "CONSTRAINT uk_spu_id UNIQUE (spu_id))");
            stmt.execute("CREATE TABLE sku (id BIGINT PRIMARY KEY, sku_id BINARY(16), spu_id BIGINT, price INT, "
                    + "CONSTRAINT uk_sku_id UNIQUE (sku_id))");
            stmt.execute("INSERT INTO spu VALUES "
                    + "(1001, X'000000000000000000000000000000A1', '手工陶瓷茶杯', 5, 10, 77),"      // 绑定运费模板
                    + "(1002, X'000000000000000000000000000000A2', '无运费模板的商品', 6, 20, NULL)"); // 未绑定
            stmt.execute("INSERT INTO sku VALUES "
                    + "(11, X'00000000000000000000000000000011', 1001, 12800),"
                    + "(12, X'00000000000000000000000000000012', 1002, 9900),"
                    + "(13, X'00000000000000000000000000000013', 9999, 5000)");                     // 商品行缺失（脏数据）
        }
        query = new SkuSpuDisplayQueryImpl(new JdbcTemplate(dataSource));
    }

    @Test
    @DisplayName("findBySkuIds — 商品名/商家/类目/运费模板四列逐列映射")
    void shouldMapAllFourSpuColumns() {
        Map<Long, SkuSpuDisplay> displays = query.findBySkuIds(List.of(11L));

        SkuSpuDisplay display = displays.get(11L);
        assertEquals("手工陶瓷茶杯", display.spuName(), "spu.name → spu_name");
        assertEquals(5L, display.sellerId(), "spu.seller_id → seller_id");
        assertEquals(10L, display.categoryId(), "spu.category_id → category_id");
        assertEquals(77L, display.freightTemplateId(), "spu.freight_template_id → freight_template_id");
        assertEquals(11L, display.skuId());
    }

    @Test
    @DisplayName("findBySkuIds — 未绑定运费模板 → 该列 null，其余三列照常有值（不回落默认模板）")
    void shouldReturnNullFreightTemplate_whenSpuNotBound() {
        SkuSpuDisplay display = query.findBySkuIds(List.of(12L)).get(12L);

        assertEquals("无运费模板的商品", display.spuName());
        assertEquals(6L, display.sellerId());
        assertEquals(20L, display.categoryId());
        assertNull(display.freightTemplateId(), "未绑定运费模板不回落，留给计价侧口径（ADR-037）");
    }

    @Test
    @DisplayName("findBySkuIds — 商品行缺失 → 键仍存在但四字段全 null（LEFT JOIN 不吞 SKU 行）")
    void shouldReturnNullFieldsButKeepRow_whenSpuMissing() {
        Map<Long, SkuSpuDisplay> displays = query.findBySkuIds(List.of(11L, 13L));

        assertTrue(displays.containsKey(13L), "商品缺失不能让 SKU 行消失（否则下单取数会把它当 SKU 不存在）");
        assertNull(displays.get(13L).spuName());
        assertNull(displays.get(13L).sellerId());
        assertNull(displays.get(13L).categoryId());
        assertNull(displays.get(13L).freightTemplateId());
    }

    @Test
    @DisplayName("findBySkuIds — 重复 ID 去重，单次查询返回单条")
    void shouldDeduplicateIds() {
        Map<Long, SkuSpuDisplay> displays = query.findBySkuIds(List.of(11L, 11L, 11L));

        assertEquals(1, displays.size());
        assertEquals("手工陶瓷茶杯", displays.get(11L).spuName());
    }

    @Test
    @DisplayName("findBySkuIds — 未知 SKU ID 不出现在结果中（调用方按缺失留空）")
    void shouldOmitUnknownSkuId() {
        Map<Long, SkuSpuDisplay> displays = query.findBySkuIds(List.of(11L, 8888L));

        assertEquals(1, displays.size());
        assertTrue(displays.containsKey(11L));
    }

    @Test
    @DisplayName("findBySkuIds — 空集合 / 全 null 入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenIdsBlank() {
        assertTrue(query.findBySkuIds(List.of()).isEmpty());
        assertTrue(query.findBySkuIds(null).isEmpty());
        assertTrue(query.findBySkuIds(Arrays.asList(null, null)).isEmpty());
    }

    // ==================== 业务键口径（ADR-061 §决策 2） ====================

    @Test
    @DisplayName("findBySkuBusinessIds — 业务键入参：返回键为业务键，并带出 SPU 业务键与展示列")
    void shouldResolveByBusinessKey_andCarryBusinessKeys() {
        Map<String, SkuSpuDisplay> displays = query.findBySkuBusinessIds(List.of(SKU_BUSINESS_ID));

        assertEquals(1, displays.size());
        SkuSpuDisplay display = displays.get(SKU_BUSINESS_ID);
        assertEquals(11L, display.skuId(), "技术键仍随行带出（写路径解析用）");
        assertEquals(SKU_BUSINESS_ID, display.skuBusinessId(), "结果键与行内 SKU 业务键一致");
        assertEquals(SPU_BUSINESS_ID, display.spuBusinessId(), "SPU 业务键 ← spu.spu_id（32-hex）");
        assertEquals("手工陶瓷茶杯", display.spuName());
        assertEquals(5L, display.sellerId());
    }

    @Test
    @DisplayName("findBySkuBusinessIds — 业务键非法/不存在 → 该键不出现（不抛异常、不放大成 500）")
    void shouldOmitUnknownBusinessKey() {
        Map<String, SkuSpuDisplay> displays = query.findBySkuBusinessIds(
                List.of(SKU_BUSINESS_ID, "prod-001", "not-a-hex-id"));

        assertEquals(1, displays.size(), "可读编码在写入端即被拒，读取端按未命中处理");
        assertTrue(displays.containsKey(SKU_BUSINESS_ID));
    }

    @Test
    @DisplayName("findBySkuBusinessIds — 空入参不发查询且返回空 Map")
    void shouldReturnEmptyMap_whenBusinessIdsBlank() {
        assertTrue(query.findBySkuBusinessIds(List.of()).isEmpty());
        assertTrue(query.findBySkuBusinessIds(null).isEmpty());
        assertTrue(query.findBySkuBusinessIds(Arrays.asList(null, null)).isEmpty());
    }
}
