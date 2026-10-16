package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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
 * 商品名前缀联想 SQL H2 集成测试（ADR-052 迁移）—— 原 search-service
 * {@code SpuSuggestDbGatewayTest} 的 SQL 语义（LIKE 前缀锚定、在售过滤、销量降序热门优先、
 * limit 截断）随数据面归属迁入 item 域；「同名去重」由应用层（RepositoryImpl.suggestNames）
 * 承载，故在此类之外另有 Manager 层覆盖。
 */
@DisplayName("SpuMapper 联想 SQL H2 集成测试（ADR-052）")
class SpuSuggestMapperH2Test {

    private SpuMapper spuMapper;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:itemsuggest_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
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
                INSERT INTO spu (id,category_id,name,description,brand,sold_count,status,publish_time) VALUES
                (1,10,'iPhone 15 手机','苹果旗舰手机','Apple',100,1,'2026-01-01 10:00:00'),
                (2,10,'iPhone 15 Pro','苹果旗舰手机','Apple',200,1,'2026-02-01 10:00:00'),
                (3,10,'小米手机','红米性价比','Xiaomi',50,1,'2026-03-01 10:00:00'),
                (4,10,'小米手机','同名单品','Xiaomi',10,1,'2026-04-01 10:00:00'),
                (5,20,'iPad 平板','苹果平板','Apple',80,0,'2026-05-01 10:00:00')
                """);   // 5 下架；3/4 同名（去重由应用层承载）
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

    @Test
    @DisplayName("suggest — 前缀锚定命中（LIKE 'prefix%'），按销量降序热门优先")
    void shouldSuggestByPrefixOrderBySoldCount() {
        List<String> names = spuMapper.selectSuggestNames("iPhone", 10);

        assertEquals(List.of("iPhone 15 Pro", "iPhone 15 手机"), names);   // 200 > 100
    }

    @Test
    @DisplayName("suggest — 下架商品不进联想（status=1 在售口径）")
    void shouldExcludeOfflineSpus() {
        assertTrue(spuMapper.selectSuggestNames("iPad", 10).isEmpty());
    }

    @Test
    @DisplayName("suggest — 无命中返回空列表")
    void shouldReturnEmpty_whenNoMatch() {
        assertTrue(spuMapper.selectSuggestNames("不存在前缀xyz", 10).isEmpty());
    }

    @Test
    @DisplayName("suggest — limit 截断生效")
    void shouldRespectLimit() {
        List<String> names = spuMapper.selectSuggestNames("iPhone", 1);

        assertEquals(List.of("iPhone 15 Pro"), names);
    }

    @Test
    @DisplayName("suggest（仓储级）— 同名去重：联想词不重复出现（原直读实现同口径）")
    void shouldDeduplicateSameName_viaRepository() {
        SpuRepositoryImpl repository = new SpuRepositoryImpl(spuMapper);

        List<String> names = repository.suggestNames("小米", 10);

        assertEquals(List.of("小米手机"), names);
    }
}
