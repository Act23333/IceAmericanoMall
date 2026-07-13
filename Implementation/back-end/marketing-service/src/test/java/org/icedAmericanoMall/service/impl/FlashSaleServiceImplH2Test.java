package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.h2.Driver;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.mapper.FlashSaleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 集成测试 — 验证 FlashSaleServiceImpl.buy() 在并发下不超卖。
 * 不使用 Spring Boot 上下文，直接构建 H2 + MyBatis-Plus；用 SqlSessionManager
 * 为每个线程分配独立会话/事务，模拟真实并发扣减。
 */
@DisplayName("FlashSaleServiceImpl H2 并发测试")
class FlashSaleServiceImplH2Test {

    private FlashSaleServiceImpl flashSaleService;

    @BeforeEach
    void setUp() throws Exception {
        // 每个用例独立库，避免相互污染
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:flashsale_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE flash_sale (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    product_id BIGINT,
                    sku_id BIGINT,
                    flash_price INT,
                    stock INT NOT NULL,
                    sold_count INT DEFAULT 0,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    status INT DEFAULT 2,
                    version INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                INSERT INTO flash_sale (id, product_id, sku_id, flash_price, stock, sold_count, status)
                VALUES (1, 100, 1000, 9900, 50, 0, 2)""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(FlashSaleMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // SqlSessionManager：每线程独立会话+事务（自动提交），支撑真实并发扣减
        SqlSessionManager sqlSessionManager = SqlSessionManager.newInstance(sqlSessionFactory);

        flashSaleService = new FlashSaleServiceImpl();
        ReflectionTestUtils.setField(flashSaleService, "baseMapper",
                sqlSessionManager.getMapper(FlashSaleMapper.class));
    }

    @Test
    @DisplayName("buy — 并发抢购不超卖：成功数等于库存，sold_count 不越界")
    void shouldNotOversell_whenConcurrentBuy() throws Exception {
        int stock = 50;
        int threads = 300;
        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (flashSaleService.buy(1L)) success.incrementAndGet();
                } catch (Exception e) {
                    soldOut.incrementAndGet(); // 已售罄 BizException
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown(); // 同时开抢
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务未在超时内完成");
        pool.shutdownNow();

        FlashSaleEntity fs = flashSaleService.getById(1L);
        assertEquals(stock, success.get(), "成功抢购数必须恰好等于库存");
        assertEquals(threads - stock, soldOut.get(), "其余请求必须全部售罄失败");
        assertEquals(stock, fs.getSoldCount(), "sold_count 不得超过库存（无超卖）");
        assertTrue(fs.getSoldCount() <= fs.getStock(), "sold_count 必须 <= stock");
    }

    @Test
    @DisplayName("buy — 售罄后再抢抛出已售罄异常")
    void shouldThrow_whenSoldOut() {
        // 先扣光 50 件
        for (int i = 0; i < 50; i++) {
            assertTrue(flashSaleService.buy(1L));
        }
        Exception ex = assertThrows(Exception.class, () -> flashSaleService.buy(1L));
        assertTrue(ex.getMessage().contains("已售罄"));
    }
}
