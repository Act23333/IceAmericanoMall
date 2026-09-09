package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.convert.ProductSearchConverterImpl;
import org.icedamericanomall.domain.vo.ProductSearchVO;
import org.icedamericanomall.mapper.ProductMapper;
import org.icedamericanomall.mapper.SkuMapper;
import org.icedamericanomall.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * DbSearchServiceImpl H2 集成测试 —— DB LIKE 搜索（关键词/类目/下架过滤/最低价补充）。
 */
@DisplayName("DbSearchServiceImpl H2 集成测试")
@SuppressWarnings("unchecked")
class DbSearchServiceImplH2Test {

    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:search_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE product (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id VARCHAR(64),
                    category_id BIGINT, name VARCHAR(100), description VARCHAR(255),
                    brand VARCHAR(50), main_image VARCHAR(255), sold_count INT DEFAULT 0,
                    status INT DEFAULT 1, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE sku (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_id VARCHAR(64), product_id BIGINT,
                    spec VARCHAR(200), price INT, stock INT, status INT DEFAULT 1,
                    sold_count INT DEFAULT 0, version INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("INSERT INTO product (id,category_id,name,description,sold_count,status) VALUES " +
                    "(1,10,'iPhone 15 手机','苹果旗舰手机',100,1)," +
                    "(2,10,'小米手机','红米性价比',50,1)," +
                    "(3,20,'iPad 平板','苹果平板',80,0)");   // 3 下架
            stmt.execute("INSERT INTO sku (product_id,price,stock,status) VALUES " +
                    "(1,699900,10,1),(1,599900,10,1),(2,199900,20,1)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ProductMapper.class);
        config.addMapper(SkuMapper.class);
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

        SkuMapper skuMapper = sqlSessionFactory.openSession().getMapper(SkuMapper.class);
        DbSearchServiceImpl impl = new DbSearchServiceImpl(
                skuMapper, new ProductSearchConverterImpl(), mock(RedisTemplate.class));
        ReflectionTestUtils.setField(impl, "baseMapper",
                sqlSessionFactory.openSession().getMapper(ProductMapper.class));
        searchService = impl;
    }

    @Test
    @DisplayName("search — 关键词命中名称/描述，按销量降序，补充最低价")
    void shouldSearchByKeyword() {
        Page<ProductSearchVO> result = searchService.search("手机", null, 1, 10);

        assertEquals(2, result.getRecords().size());
        assertEquals("iPhone 15 手机", result.getRecords().get(0).getName()); // 销量高在前
        assertEquals(599900, result.getRecords().get(0).getPrice());          // 两个 sku 取最低价
    }

    @Test
    @DisplayName("search — 按类目过滤，且排除下架商品")
    void shouldFilterByCategoryAndExcludeOffline() {
        Page<ProductSearchVO> result = searchService.search(null, 10L, 1, 10);
        assertEquals(2, result.getRecords().size());   // 类目10 的上架商品

        // 类目20 只有下架的 iPad → 空
        assertTrue(searchService.search(null, 20L, 1, 10).getRecords().isEmpty());
    }

    @Test
    @DisplayName("search — 无匹配返回空页")
    void shouldReturnEmpty_whenNoMatch() {
        assertTrue(searchService.search("不存在关键词xyz", null, 1, 10).getRecords().isEmpty());
    }
}
