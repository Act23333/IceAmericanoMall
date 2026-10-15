package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.h2.Driver;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.enums.ScopeTypeEnum;
import org.icedamericanomall.marketing.domain.enums.StackRuleEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code CouponRepositoryImpl} 适用范围/叠加规则四字段（{@code scope_type}/{@code scope_values}/
 * {@code stack_rule}/{@code stack_group}）的 H2 往返测试（V4.3 列映射打通回归）。
 *
 * <p>覆盖 ADR-038 记录的历史缺口：这四列此前未映射进 PO/聚合，建券时静默落 DB 默认值。
 * 测试口径：{@code save} → 重新 {@code findById} 读回 → 四字段等价；且 {@code updateById}
 * 路径（MyBatis-Plus {@code update-strategy=not_null}）同样带上这四字段。
 *
 * <p>H2 建表用 {@code VARCHAR} 承载 {@code scope_values}（H2 的 {@code JSON} 类型会把写入的
 * 数组字符串当作 JSON 标量多包一层引号，与 MySQL 语义不同），因此这里验证的是 PO↔聚合
 * 映射的字段通道，JSON 形态容错另由 {@code CouponScopeTest} 覆盖。
 */
@DisplayName("CouponRepositoryImpl — scope/stack 四字段 H2 往返")
class CouponRepositoryImplH2Test {

    private static final String COUPON_NO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    private CouponRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:couponrepo_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE coupon (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    coupon_id BINARY(16),
                    name VARCHAR(64),
                    type INT,
                    `value` INT,
                    min_amount INT,
                    total_qty INT,
                    issued_qty INT,
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    status INT,
                    seller_id BIGINT,
                    discount_type INT,
                    coupon_category INT,
                    grant_type INT,
                    stock_type INT,
                    grab_type INT,
                    price_in_cents INT,
                    scope_type INT DEFAULT 1,
                    scope_values VARCHAR(512),
                    stack_rule INT DEFAULT 1,
                    stack_group VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(CouponMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionManager sqlSessionManager = SqlSessionManager.newInstance(sqlSessionFactory);

        repository = new CouponRepositoryImpl(sqlSessionManager.getMapper(CouponMapper.class));
    }

    @Test
    @DisplayName("save → findById — scope/stack 四字段完整往返（不再落 DB 默认值）")
    void shouldRoundTripScopeAndStackFields_whenSaveThenFindById() {
        Coupon saved = repository.save(limitedCoupon());
        assertNotNull(saved.getId());

        Coupon loaded = repository.findById(saved.getId());

        assertNotNull(loaded);
        assertEquals(ScopeTypeEnum.PRODUCT.getCode(), loaded.getScopeType());
        assertEquals("[\"1001\",\"1002\"]", loaded.getScopeValues());
        assertEquals(StackRuleEnum.STACKABLE.getCode(), loaded.getStackRule());
        assertEquals("GROUP-A", loaded.getStackGroup());
        assertEquals(COUPON_NO, loaded.getCouponId());
    }

    @Test
    @DisplayName("update → findById — 四字段可被改写（updateById 路径同样带列映射）")
    void shouldRoundTripScopeAndStackFields_whenUpdate() {
        Coupon saved = repository.save(limitedCoupon());

        saved.setScopeType(ScopeTypeEnum.CATEGORY.getCode());
        saved.setScopeValues("[7,8]");
        saved.setStackRule(StackRuleEnum.MUTUAL_EXCLUSIVE.getCode());
        saved.setStackGroup(null);
        repository.update(saved);

        Coupon loaded = repository.findById(saved.getId());
        assertEquals(ScopeTypeEnum.CATEGORY.getCode(), loaded.getScopeType());
        assertEquals("[7,8]", loaded.getScopeValues());
        assertEquals(StackRuleEnum.MUTUAL_EXCLUSIVE.getCode(), loaded.getStackRule());
        // not_null 更新策略下 null 不覆写既有值 —— 这是既有 MyBatis 配置口径，非本任务改动
        assertEquals("GROUP-A", loaded.getStackGroup());
    }

    @Test
    @DisplayName("findByCouponId — 业务券号检索同样带出四字段")
    void shouldRoundTripScopeAndStackFields_whenFindByCouponId() {
        repository.save(limitedCoupon());

        Coupon loaded = repository.findByCouponId(COUPON_NO);

        assertNotNull(loaded);
        assertEquals(ScopeTypeEnum.PRODUCT.getCode(), loaded.getScopeType());
        assertEquals("[\"1001\",\"1002\"]", loaded.getScopeValues());
        assertEquals(StackRuleEnum.STACKABLE.getCode(), loaded.getStackRule());
        assertEquals("GROUP-A", loaded.getStackGroup());
    }

    @Test
    @DisplayName("toDomain(null) → null（未命中不放大为异常）")
    void shouldReturnNull_whenPoNull() {
        assertNull(CouponRepositoryImpl.toDomain(null));
    }

    @Test
    @DisplayName("findActiveTemplates — 生效券列表带出四字段")
    void shouldRoundTripScopeAndStackFields_whenFindActiveTemplates() {
        repository.save(limitedCoupon());

        List<Coupon> actives = repository.findActiveTemplates(LocalDateTime.now().minusDays(1));

        assertEquals(1, actives.size());
        assertEquals(ScopeTypeEnum.PRODUCT.getCode(), actives.get(0).getScopeType());
        assertEquals("[\"1001\",\"1002\"]", actives.get(0).getScopeValues());
        assertTrue(actives.get(0).getStackGroup().contains("GROUP"));
    }

    private static Coupon limitedCoupon() {
        return Coupon.builder()
                .couponId(COUPON_NO)
                .name("限定单品可叠加券")
                .type(1)
                .value(1000)
                .minAmount(5000)
                .totalQty(100)
                .issuedQty(0)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(30))
                .status(CouponStatusEnum.ACTIVE.getCode())
                .discountType(1)
                .couponCategory(1)
                .grantType(1)
                .stockType(1)
                .grabType(1)
                .scopeType(ScopeTypeEnum.PRODUCT.getCode())
                .scopeValues("[\"1001\",\"1002\"]")
                .stackRule(StackRuleEnum.STACKABLE.getCode())
                .stackGroup("GROUP-A")
                .build();
    }
}
