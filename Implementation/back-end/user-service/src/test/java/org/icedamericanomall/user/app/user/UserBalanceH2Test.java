package org.icedamericanomall.user.app.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.infrastructure.persistence.user.UserMapper;
import org.icedamericanomall.user.infrastructure.persistence.user.UserRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 余额链路 H2 集成测试（重构后版本）—— UserManager + 真实 UserRepositoryImpl（H2）。
 *
 * <p>验证原子扣减 / 余额不足 / 充值，不启动 Spring 上下文。
 */
@DisplayName("UserManager 余额 H2 集成测试")
class UserBalanceH2Test {

    private UserManager userManager;

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
                    user_id VARCHAR(64), username VARCHAR(50), avatar VARCHAR(255),
                    gender INT DEFAULT 0, birthday DATE, member_level VARCHAR(20) DEFAULT 'NORMAL',
                    phone_cipher VARCHAR(512), phone_hash VARCHAR(128), phone_mask VARCHAR(64),
                    email_cipher VARCHAR(512), email_hash VARCHAR(128), email_mask VARCHAR(64),
                    register_time TIMESTAMP NULL, status INT DEFAULT 1, balance INT DEFAULT 0,
                    version INT DEFAULT 0,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL
                )""");
            stmt.execute("INSERT INTO user (id, user_id, status, balance) "
                    + "VALUES (100, 'u-100', 1, 10000)");
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

        UserRepositoryImpl userRepository = new UserRepositoryImpl(
                sqlSessionFactory.openSession().getMapper(UserMapper.class));
        // 余额链路不经过 UserProvisioner / 角色仓储
        userManager = new UserManager(userRepository, null, null);
    }

    @Test
    @DisplayName("getBalance — 返回当前余额")
    void shouldReturnBalance() {
        assertEquals(10000, userManager.getBalance(100L));
    }

    @Test
    @DisplayName("deductBalance — 余额充足则扣减成功")
    void shouldDeduct_whenSufficient() {
        userManager.deductBalance(100L, 3000);
        assertEquals(7000, userManager.getBalance(100L));
    }

    @Test
    @DisplayName("deductBalance — 余额不足抛 BizException 且不扣减")
    void shouldThrow_whenInsufficient() {
        assertThrows(BizException.class, () -> userManager.deductBalance(100L, 999999));
        assertEquals(10000, userManager.getBalance(100L), "扣减失败余额不变");
    }

    @Test
    @DisplayName("addBalance — 充值后余额增加")
    void shouldRecharge() {
        userManager.addBalance(100L, 5000);
        assertEquals(15000, userManager.getBalance(100L));
    }
}
