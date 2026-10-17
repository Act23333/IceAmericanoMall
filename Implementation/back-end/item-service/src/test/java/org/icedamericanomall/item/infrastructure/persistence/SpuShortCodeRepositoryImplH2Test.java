package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code spu.short_code} 读写 H2 集成测试（ADR-063 §决策 8，批次 B7b）—— 在真 SQL 上锁定三件事：
 *
 * <ol>
 *   <li><b>等值查命中</b>：{@code SELECT p.* FROM spu p WHERE p.short_code = ?} 走唯一索引；</li>
 *   <li><b>不过滤 {@code status}</b>：软删（{@code status=3}）的行<b>仍能被短码查到</b> ——
 *       短码永不复用、删除态仍算「已占用」，状态判断属应用层（ADR-063 §决策 8.4）；</li>
 *   <li><b>唯一约束是硬约束</b>：重复 {@code short_code} 插入必须失败（发布时靠它做碰撞硬失败；
 *       重试逻辑在应用层，DB 不做静默覆盖）。</li>
 * </ol>
 */
@DisplayName("spu.short_code H2 集成测试（ADR-063 §决策 8）")
class SpuShortCodeRepositoryImplH2Test {

    private static final String ON_SALE_CODE = "8f3a1c9d20";
    private static final String DELETED_CODE = "aaaaaaaaaa";

    private SpuRepositoryImpl repository;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:itemshortcode_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE spu (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    spu_id BINARY(16) NOT NULL,
                    short_code VARCHAR(16) NOT NULL,
                    seller_id BIGINT, category_id BIGINT, freight_template_id BIGINT,
                    name VARCHAR(100), main_image VARCHAR(255), images VARCHAR(2000),
                    video_url VARCHAR(500), description VARCHAR(255), brand VARCHAR(50),
                    sold_count INT DEFAULT 0, comment_count INT DEFAULT 0, is_ad INT DEFAULT 0,
                    status INT DEFAULT 1, publish_time TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_short_code UNIQUE (short_code)
                )""");
            stmt.execute("""
                INSERT INTO spu (id,spu_id,short_code,seller_id,category_id,name,status) VALUES
                (1, X'00000000000000000000000000000001', '""" + ON_SALE_CODE + "', 5, 10, '在售商品', 1),\n"
                    + "(2, X'00000000000000000000000000000002', '" + DELETED_CODE + "', 5, 10, '已删商品', 3)");
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
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        repository = new SpuRepositoryImpl(sqlSessionFactory.openSession().getMapper(SpuMapper.class));
    }

    @Test
    @DisplayName("findByShortCode — 命中返回聚合（业务键出 hex、短码原样带回、状态如实）")
    void shouldResolveByShortCode() {
        var spu = repository.findByShortCode(ON_SALE_CODE);

        assertNotNull(spu);
        assertEquals("00000000000000000000000000000001", spu.getSpuId());
        assertEquals(ON_SALE_CODE, spu.getShortCode());
        assertEquals(1, spu.getStatus());
        assertEquals("在售商品", spu.getName());
    }

    @Test
    @DisplayName("findByShortCode — 软删（status=3）仍可查到：短码不回收，状态判断留给应用层")
    void shouldStillResolveDeletedRow() {
        var spu = repository.findByShortCode(DELETED_CODE);

        assertNotNull(spu, "短码永不复用 ⇒ 删除态必须仍能按码查到（否则「码还在、商品删了」会被误判成未分配）");
        assertEquals(3, spu.getStatus());
    }

    @Test
    @DisplayName("findByShortCode — 未分配 / 形态非法 → null（不放大成 500）")
    void shouldReturnNullWhenAbsentOrMalformed() {
        assertNull(repository.findByShortCode("0123456789"));
        assertNull(repository.findByShortCode("8F3A1C9D20"));
        assertNull(repository.findByShortCode("7"));
        assertNull(repository.findByShortCode(null));
    }

    @Test
    @DisplayName("uk_short_code — 重复短码插入必须失败（碰撞即硬失败，DB 不做静默覆盖）")
    void shouldRejectDuplicateShortCode() throws Exception {
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            var ex = assertThrows(java.sql.SQLException.class, () -> stmt.execute(
                    "INSERT INTO spu (id,spu_id,short_code,seller_id,category_id,name,status) VALUES "
                            + "(3, X'00000000000000000000000000000003', '" + ON_SALE_CODE + "', 5, 10, '撞码商品', 1)"));
            assertTrue(String.valueOf(ex.getMessage()).toUpperCase().contains("UK_SHORT_CODE")
                            || String.valueOf(ex.getMessage()).toUpperCase().contains("UNIQUE"),
                    "冲突必须由唯一约束拦下: " + ex.getMessage());
        }
    }
}
