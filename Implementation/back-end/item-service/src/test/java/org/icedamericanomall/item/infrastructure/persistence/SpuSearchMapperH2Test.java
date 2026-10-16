package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商品检索 SQL H2 集成测试（ADR-052 迁移）—— 原 search-service
 * {@code DbSpuSearchGatewayTest} / {@code SpuSuggestDbGatewayTest} 的 SQL 语义
 * 随数据面归属迁入 item 域（{@code SpuMapper#selectSearchPage/selectExportPage/
 * selectSuggestNames/selectSearchCardById}），逐条锁死：
 * 关键词 LIKE(name/description) / 分类 / 品牌 / 在售 SKU 最低价区间（COALESCE 0 口径）/
 * 排序（sales|price|newest，未知回落销量）/ 在售过滤 / 前缀联想（销量降序 + 去重 + 截断）。
 */
@DisplayName("SpuMapper 检索 SQL H2 集成测试（ADR-052）")
class SpuSearchMapperH2Test {

    private SpuMapper spuMapper;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:itemsearch_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE spu (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, spu_id BINARY(16),
                    category_id BIGINT, name VARCHAR(100), description VARCHAR(255),
                    brand VARCHAR(50), main_image VARCHAR(255), sold_count INT DEFAULT 0,
                    status INT DEFAULT 1, publish_time TIMESTAMP, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE sku (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, spu_id BIGINT,
                    spec VARCHAR(200), price INT, stock INT, status INT DEFAULT 1,
                    sold_count INT DEFAULT 0, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                INSERT INTO spu (id,spu_id,category_id,name,description,brand,sold_count,status,publish_time) VALUES
                (1,X'00000000000000000000000000000001',10,'iPhone 15 手机','苹果旗舰手机','Apple',100,1,'2026-01-01 10:00:00'),
                (2,X'00000000000000000000000000000002',10,'小米手机','红米性价比','Xiaomi',50,1,'2026-03-01 10:00:00'),
                (3,X'00000000000000000000000000000003',20,'iPad 平板','苹果平板','Apple',80,0,'2026-02-01 10:00:00')
                """);   // 3 下架
            stmt.execute("INSERT INTO sku (spu_id,price,stock,status) VALUES "
                    + "(1,699900,10,1),(1,599900,10,1),(2,199900,20,1)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SpuMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        factoryBean.setPlugins(interceptor);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        spuMapper = sqlSessionFactory.openSession().getMapper(SpuMapper.class);
    }

    private IPage<SpuPO> search(String keyword, Long categoryId, String brand,
                                    Integer minPrice, Integer maxPrice, String sort) {
        return spuMapper.selectSearchPage(new Page<>(1, 10), keyword, categoryId, brand, minPrice, maxPrice, sort);
    }

    @Test
    @DisplayName("search — 关键词命中名称/描述，按销量降序，计算列 min_price 补齐且业务 ID 保持 BINARY")
    void shouldSearchByKeyword() {
        IPage<SpuPO> result = search("手机", null, null, null, null, null);

        assertEquals(2, result.getRecords().size());
        SpuPO top = result.getRecords().get(0);
        assertEquals("iPhone 15 手机", top.getName());     // 销量高在前
        assertEquals(599900, top.getMinPrice());           // 两个 sku 取最低价
        assertEquals("00000000000000000000000000000001",
                org.icedamericanomall.common.utils.IdGenerator.toHexString(top.getSpuId()));
    }

    @Test
    @DisplayName("search — 按类目过滤，且排除下架商品")
    void shouldFilterByCategoryAndExcludeOffline() {
        assertEquals(2, search(null, 10L, null, null, null, null).getRecords().size());
        assertTrue(search(null, 20L, null, null, null, null).getRecords().isEmpty());   // 类目20 仅下架
    }

    @Test
    @DisplayName("search — 无匹配返回空页")
    void shouldReturnEmpty_whenNoMatch() {
        assertTrue(search("不存在关键词xyz", null, null, null, null, null).getRecords().isEmpty());
    }

    @Test
    @DisplayName("SE1 — brand 过滤只命中该品牌在售商品")
    void shouldFilterByBrand_whenBrandProvided() {
        IPage<SpuPO> result = search(null, null, "Apple", null, null, null);

        assertEquals(1, result.getRecords().size());   // id=3 下架被排除
        assertEquals("iPhone 15 手机", result.getRecords().get(0).getName());
    }

    @Test
    @DisplayName("SE1 — 价格区间按在售 SKU 最低价口径（双边界）")
    void shouldFilterByPriceRange_whenRangeProvided() {
        IPage<SpuPO> result = search(null, null, null, 500_000, 600_000, null);

        assertEquals(1, result.getRecords().size());   // 小米最低价 199900 不在区间
        assertEquals("iPhone 15 手机", result.getRecords().get(0).getName());
    }

    @Test
    @DisplayName("SE1 — 仅 maxPrice 过滤 → 单边界生效")
    void shouldFilterByUpperBound_whenOnlyMaxPriceProvided() {
        IPage<SpuPO> result = search(null, null, null, null, 300_000, null);

        assertEquals(1, result.getRecords().size());
        assertEquals("小米手机", result.getRecords().get(0).getName());
    }

    @Test
    @DisplayName("SE1 — sort=newest → publish_time 降序（非销量序）")
    void shouldSortByPublishTimeDesc_whenSortNewest() {
        IPage<SpuPO> result = search(null, null, null, null, null, "newest");

        assertEquals("小米手机", result.getRecords().get(0).getName());      // 2026-03 最新在前
        assertEquals("iPhone 15 手机", result.getRecords().get(1).getName());
    }

    @Test
    @DisplayName("SE1 — sort=price → 最低价升序")
    void shouldSortByMinPriceAsc_whenSortPrice() {
        IPage<SpuPO> result = search(null, null, null, null, null, "price");

        assertEquals("小米手机", result.getRecords().get(0).getName());      // 199900 < 599900
        assertEquals(199900, result.getRecords().get(0).getMinPrice());
    }

    @Test
    @DisplayName("SE1 — sort=未知取值 → 回落销量降序现行为（relevance）")
    void shouldFallbackToSoldCountOrder_whenSortUnknown() {
        IPage<SpuPO> result = search(null, null, null, null, null, "bogus");

        assertEquals("iPhone 15 手机", result.getRecords().get(0).getName());  // 销量 100 > 50
    }

    @Test
    @DisplayName("export — 在售全量导出：id 升序稳定遍历，total 反映全集")
    void shouldExportOnSaleByIdAsc() {
        IPage<SpuPO> result = spuMapper.selectExportPage(new Page<>(1, 10));

        assertEquals(2, result.getTotal());             // 下架不入导出
        assertEquals("iPhone 15 手机", result.getRecords().get(0).getName());
        assertEquals("小米手机", result.getRecords().get(1).getName());
    }

    // ==================== 业务键口径（ADR-061 §决策 2；ADR-062 §决策 4 单形态） ====================

    @Test
    @DisplayName("detail — 按 32-hex 业务键命中（spu_id 唯一索引，同库内部技术键不变）")
    void shouldLoadSearchCardByBusinessId() {
        SpuPO card = spuMapper.selectSearchCardByBusinessId(
                org.icedamericanomall.common.utils.IdGenerator.toBytes("00000000000000000000000000000001"));

        assertEquals(1L, card.getId());
        assertEquals(599900, card.getMinPrice());
        assertEquals("Apple", card.getBrand());
        // publishTime（ES 回填用，SE1 newest 排序）随同一次往返取回
        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 10, 0), card.getPublishTime());
    }

    @Test
    @DisplayName("detail — 未知业务键返回 null（不放大成异常）")
    void shouldReturnNullCard_whenBusinessIdMissing() {
        assertEquals(null, spuMapper.selectSearchCardByBusinessId(
                org.icedamericanomall.common.utils.IdGenerator.toBytes("00000000000000000000000000000009")));
    }

    @Test
    @DisplayName("批量解析 — 业务键 → 技术键只含命中项，且结果键为 32-hex")
    void shouldMapIdByBusinessIds() {
        List<SpuBusinessKeyRow> rows = spuMapper.selectIdByBusinessIds(List.of(
                org.icedamericanomall.common.utils.IdGenerator.toBytes("00000000000000000000000000000001"),
                org.icedamericanomall.common.utils.IdGenerator.toBytes("00000000000000000000000000000003"),
                org.icedamericanomall.common.utils.IdGenerator.toBytes("00000000000000000000000000000009")));

        assertEquals(2, rows.size());
        assertEquals("00000000000000000000000000000001",
                org.icedamericanomall.common.utils.IdGenerator.toHexString(rows.get(0).getSpuId()));
        assertEquals(1L, rows.get(0).getId());
        assertEquals("00000000000000000000000000000003",
                org.icedamericanomall.common.utils.IdGenerator.toHexString(rows.get(1).getSpuId()));
        assertEquals(3L, rows.get(1).getId());
    }
}
