package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedAmericanoMall.convert.UserConverter;
import org.icedAmericanoMall.mapper.UserMapper;
import org.icedAmericanoMall.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 余额服务 H2 集成测试 —— 验证原子扣减 / 余额不足 / 充值。
 */
@DisplayName("UserService 余额 H2 集成测试")
class BalanceServiceH2Test {

    private UserService userService;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:balance_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(64), username VARCHAR(50), phone VARCHAR(20),
                    password VARCHAR(100), avatar VARCHAR(255), wx_openid VARCHAR(128),
                    register_time TIMESTAMP, status INT DEFAULT 1, balance INT DEFAULT 0,
                    role_type INT DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("INSERT INTO user (id, user_id, phone, status, balance, role_type) " +
                    "VALUES (100, 'u-100', '13800138000', 1, 10000, 0)");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
        config.addMapper(UserMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        UserServiceImpl impl = new UserServiceImpl(mock(UserConverter.class));
        ReflectionTestUtils.setField(impl, "baseMapper",
                sqlSessionFactory.openSession().getMapper(UserMapper.class));
        userService = impl;
    }

    @Test
    @DisplayName("getBalance — 返回当前余额")
    void shouldReturnBalance() {
        assertEquals(10000, userService.getBalance(100L));
    }

    @Test
    @DisplayName("deductBalance — 余额充足则扣减成功")
    void shouldDeduct_whenSufficient() {
        userService.deductBalance(100L, 3000);
        assertEquals(7000, userService.getBalance(100L));
    }

    @Test
    @DisplayName("deductBalance — 余额不足抛 BizException 且不扣减")
    void shouldThrow_whenInsufficient() {
        assertThrows(BizException.class, () -> userService.deductBalance(100L, 999999));
        assertEquals(10000, userService.getBalance(100L), "扣减失败余额不变");
    }

    @Test
    @DisplayName("addBalance — 充值后余额增加")
    void shouldRecharge() {
        userService.addBalance(100L, 5000);
        assertEquals(15000, userService.getBalance(100L));
    }
}
