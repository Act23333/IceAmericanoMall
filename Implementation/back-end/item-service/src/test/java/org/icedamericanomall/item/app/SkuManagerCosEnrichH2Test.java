package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.dto.SkuCO;
import org.icedamericanomall.item.adapter.assembler.SkuConverterImpl;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.repository.SkuRepository;
import org.icedamericanomall.item.infrastructure.persistence.SkuMapper;
import org.icedamericanomall.item.infrastructure.persistence.SkuSpuDisplayQueryImpl;
import org.icedamericanomall.item.infrastructure.persistence.SkuRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I13（{@code GET /internal/item/sku/list/batch}）<b>非 mock</b> H2 集成测试 ——
 * 锁死「SKU 本体 + SPU 展示列 + 两个业务键」端到端取数语义（ADR-057 + ADR-061）。
 *
 * <p><b>缺陷背景（线上疑似）</b>：{@code sku} 表没有商品名列，而 {@code SkuCO} 的
 * {@code spu_name} / {@code seller_id} / {@code category_id} / {@code freight_template_id}
 * 只能来自 {@code spu} 表。修复前 I13 = 纯 {@code sku} 表查询 + MapStruct 投影，
 * 字段<b>无源可映射、恒为 null</b>；上层单测因为 mock 了 {@code SkuCO} 而完全看不见。
 *
 * <p><b>本测试为何不能用 mock</b>：链路上的真实仓储（MyBatis-Plus → H2）、真实 MapStruct
 * 投影与真实联表 SQL 同时在场，才能证明「展示列真的有源」；业务键列按 {@code Initialize.sql}
 * 的形态（{@code BINARY(16)} + 唯一索引）建表，故「业务键 → 技术键 / 技术键 → 业务键」
 * 两个方向都走真实 SQL（{@code byte[]} 比较），而不是被 mock 掩盖。
 */
@DisplayName("SkuManager — I13 商品字段回填 H2 集成测试（非 mock）")
class SkuManagerCosEnrichH2Test {

    /** 行 11 的 SKU 业务键（H2 的 BINARY(16) 小写 hex 形态）。 */
    private static final String SKU_BUSINESS_ID = "00000000000000000000000000000011";
    /** 行 11 所属 SPU 的业务键。 */
    private static final String SPU_BUSINESS_ID = "000000000000000000000000000000a1";
    /** 行 13（商品行缺失的孤儿 SKU）的业务键。 */
    private static final String ORPHAN_SKU_BUSINESS_ID = "00000000000000000000000000000013";

    private SkuRepository skuRepository;
    private SkuManager skuManager;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:sku_cos_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE sku (
                    id BIGINT PRIMARY KEY, sku_id BINARY(16), spu_id BIGINT,
                    spec VARCHAR(200), price INT, original_price INT, stock INT,
                    image VARCHAR(255), sold_count INT, stock_type INT, is_hot BOOLEAN,
                    hot_reason VARCHAR(255), sales_tags VARCHAR(500), status INT,
                    version INT, create_time TIMESTAMP, update_time TIMESTAMP
                )""");
            stmt.execute("CREATE UNIQUE INDEX uk_sku_id ON sku (sku_id)");
            stmt.execute("CREATE TABLE spu ("
                    + "id BIGINT PRIMARY KEY, spu_id BINARY(16), name VARCHAR(200), seller_id BIGINT, "
                    + "category_id BIGINT, freight_template_id BIGINT, "
                    + "CONSTRAINT uk_spu_id UNIQUE (spu_id))");
            stmt.execute("INSERT INTO spu VALUES (1001, X'000000000000000000000000000000A1', "
                    + "'手工陶瓷茶杯', 5, 10, 77)");
            stmt.execute("INSERT INTO sku VALUES "
                    + "(11, X'00000000000000000000000000000011', 1001, '白瓷 300ml', 12800, 15800, 20, "
                    + "'cup.jpg', 3, 1, TRUE, NULL, NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),"
                    + "(13, X'00000000000000000000000000000013', 9999, '孤儿 SKU', 5000, NULL, 7, "
                    + "'lost.jpg', 0, 1, FALSE, NULL, NULL, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SkuMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        skuRepository = new SkuRepositoryImpl(sqlSessionFactory.openSession().getMapper(SkuMapper.class));
        skuManager = new SkuManager(skuRepository, new SkuConverterImpl(),
                new SkuSpuDisplayQueryImpl(new JdbcTemplate(dataSource)));
    }

    @Test
    @DisplayName("I13 — 修复前展示列与业务键恒空；修复后（联表 spu）全部非空且标识为业务键")
    void shouldFillSpuFields_onlyAfterJoinEnrichment() {
        // 修复前链路：sku 表 → 聚合 → MapStruct 投影 —— 展示列无源、业务键同名异义被 ignore，必然为空
        List<SkuCO> beforeFix = new SkuConverterImpl().toCOs(skuRepository.findByIds(List.of(11L)));
        assertNull(beforeFix.get(0).getSpuName(), "sku 表无商品名列：投影无源 → 线上 spu_name 恒空");
        assertNull(beforeFix.get(0).getSellerId());
        assertNull(beforeFix.get(0).getCategoryId());
        assertNull(beforeFix.get(0).getFreightTemplateId());
        assertNull(beforeFix.get(0).getSpuId(), "技术外键不得被自动转换成业务键（ADR-058 §4）");

        // 修复后链路：I13 入参为 SKU 业务键，一次联表同时回填展示列与两个业务键
        List<SkuCO> afterFix = skuManager.getCosByIds(List.of(SKU_BUSINESS_ID));

        SkuCO co = afterFix.get(0);
        assertEquals("手工陶瓷茶杯", co.getSpuName(), "spu.name → spu_name（ADR-061 §决策 5）");
        assertEquals(SPU_BUSINESS_ID, co.getSpuId(), "spu.spu_id → SPU 业务键（32-hex）");
        assertEquals(SKU_BUSINESS_ID, co.getSkuId(), "sku.sku_id → SKU 业务键（32-hex）");
        assertEquals(5L, co.getSellerId(), "spu.seller_id → sellerId（下单快照/单店铺校验依赖）");
        assertEquals(10L, co.getCategoryId(), "spu.category_id → categoryId（券 scope 校验依赖）");
        assertEquals(77L, co.getFreightTemplateId(), "spu.freight_template_id → freightTemplateId（运费计价依赖）");
        // 本体字段不受回填影响
        assertEquals("白瓷 300ml", co.getSpec());
        assertEquals(Integer.valueOf(12800), co.getPrice());
        assertEquals(Integer.valueOf(20), co.getStock());
    }

    @Test
    @DisplayName("I13 — 商品行缺失：SKU 本体照常返回、商品侧字段为 null、不抛异常（fail-open）")
    void shouldKeepSkuRow_whenSpuMissing() {
        List<SkuCO> cos = skuManager.getCosByIds(List.of(ORPHAN_SKU_BUSINESS_ID));

        assertEquals(1, cos.size(), "商品缺失不能等价于 SKU 不存在（否则购物车会误判下架）");
        SkuCO co = cos.get(0);
        assertEquals(Integer.valueOf(5000), co.getPrice(), "本体价格必须有值，否则该项无法计价");
        assertEquals(ORPHAN_SKU_BUSINESS_ID, co.getSkuId(), "SKU 业务键来自 sku 行，不依赖 spu 行");
        assertNull(co.getSpuName(), "商品行缺失 → 商品侧字段留空（fail-open，不回落技术键）");
        assertNull(co.getSpuId());
        assertNull(co.getSellerId());
        assertNull(co.getCategoryId());
        assertNull(co.getFreightTemplateId());
    }

    @Test
    @DisplayName("I13 — 业务键格式非法：按未命中处理，返回空列表（不放大成 500）")
    void shouldReturnEmpty_whenBusinessKeyMalformed() {
        List<SkuCO> cos = assertDoesNotThrow(() -> skuManager.getCosByIds(List.of("prod-001", "not-hex")));

        assertTrue(cos.isEmpty());
    }

    @Test
    @DisplayName("I13 — 展示查询故障：返回空结果、不抛异常（fail-open，不放大为主接口失败）")
    void shouldFailOpen_whenDisplayQueryThrows() {
        AtomicInteger calls = new AtomicInteger();
        SkuSpuDisplayQuery broken = new SkuSpuDisplayQuery() {
            @Override
            public Map<Long, SkuSpuDisplay> findBySkuIds(Collection<Long> skuIds) {
                calls.incrementAndGet();
                throw new IllegalStateException("spu 表不可用");
            }

            @Override
            public Map<String, SkuSpuDisplay> findBySkuBusinessIds(Collection<String> skuBusinessIds) {
                calls.incrementAndGet();
                throw new IllegalStateException("spu 表不可用");
            }
        };
        SkuManager degraded = new SkuManager(skuRepository, new SkuConverterImpl(), broken);

        List<SkuCO> cos = assertDoesNotThrow(() -> degraded.getCosByIds(List.of(SKU_BUSINESS_ID)));

        assertEquals(1, calls.get(), "整批只查一次（无 N+1）");
        assertTrue(cos.isEmpty(), "读模型故障 → 空结果（fail-open，绝不外溢成 500）");
    }

    @Test
    @DisplayName("I13 — 空入参：不发查询、返回空列表")
    void shouldReturnEmpty_whenIdsBlank() {
        AtomicInteger calls = new AtomicInteger();
        SkuSpuDisplayQuery counting = new SkuSpuDisplayQuery() {
            @Override
            public Map<Long, SkuSpuDisplay> findBySkuIds(Collection<Long> skuIds) {
                calls.incrementAndGet();
                return Map.of();
            }

            @Override
            public Map<String, SkuSpuDisplay> findBySkuBusinessIds(Collection<String> skuBusinessIds) {
                calls.incrementAndGet();
                return Map.of();
            }
        };
        SkuManager manager = new SkuManager(skuRepository, new SkuConverterImpl(), counting);

        assertTrue(manager.getCosByIds(List.of()).isEmpty());
        assertTrue(manager.getCosByIds(null).isEmpty());
        assertEquals(0, calls.get(), "空入参不应触发商品展示查询");
    }
}
