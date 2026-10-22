package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.trade.domain.repository.WithdrawalPayoutAttemptRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalPayoutAttemptRepository.AttemptState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出款尝试账的真库用例（H2，MODE=MySQL）。
 *
 * <p>并发是真并发：LockSemanticsH2Test 已实测 H2 的 FOR UPDATE 会阻塞，
 * 所以这里用两条线程真抢行锁，而不是用"串行等价"糊过去。
 */
@SpringJUnitConfig(WithdrawalPayoutAttemptH2Test.TestConfig.class)
@DisplayName("出款尝试账（H2 真库）")
class WithdrawalPayoutAttemptH2Test {

    private static final Long WITHDRAWAL_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Autowired
    private WithdrawalPayoutAttemptRepository repository;

    @Autowired
    private WithdrawalPayoutAttemptMapper mapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void reset() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS withdrawal (
                    id BIGINT PRIMARY KEY,
                    withdrawal_no VARCHAR(64) NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    payout_no VARCHAR(64),
                    last_payout_attempt_at TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS withdrawal_payout_attempt (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    withdrawal_id BIGINT NOT NULL,
                    attempt_no INT NOT NULL,
                    payout_no VARCHAR(64) NOT NULL,
                    channel VARCHAR(32) NOT NULL,
                    state TINYINT NOT NULL,
                    channel_ref VARCHAR(128),
                    channel_message VARCHAR(500),
                    requested_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_payout_no UNIQUE (payout_no),
                    CONSTRAINT uk_withdrawal_attempt UNIQUE (withdrawal_id, attempt_no)
                )""");
            stmt.execute("DELETE FROM withdrawal_payout_attempt");
            stmt.execute("DELETE FROM withdrawal");
            stmt.execute("INSERT INTO withdrawal(id, withdrawal_no, status) VALUES (1, 'WD-TEST-1', 3)");
        }
    }

    @Test
    @DisplayName("真并发占号：两线程同时发起，只有一次成功")
    void shouldAllowOnlyOneConcurrentClaim() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> taskA = () -> claimOrFalse(barrier, "PN-A");
            Callable<Boolean> taskB = () -> claimOrFalse(barrier, "PN-B");
            List<Future<Boolean>> futures = pool.invokeAll(List.of(taskA, taskB), 10, TimeUnit.SECONDS);
            long success = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get())) {
                    success++;
                }
            }
            assertEquals(1L, success, "必须恰好一次成功：两次都成功说明行锁没生效");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, countAll(), "账上只能有一条尝试");
    }

    @Test
    @DisplayName("已存在非终态尝试 ⇒ 再占号被拒")
    void shouldRejectSecondClaim_whenNonTerminalExists() {
        repository.claimNewAttempt(WITHDRAWAL_ID, "PN-1", "MOCK", NOW);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> repository.claimNewAttempt(WITHDRAWAL_ID, "PN-2", "MOCK", NOW));
        assertTrue(ex.getMessage().contains("未收敛"), "报错要说明原因，不能只说失败");
        assertEquals(1, countAll());
    }

    @Test
    @DisplayName("撞 UNIQUE(withdrawal_id, attempt_no) 直接抛，不静默重试")
    void shouldThrowOnUniqueViolation() {
        repository.claimNewAttempt(WITHDRAWAL_ID, "PN-1", "MOCK", NOW);

        WithdrawalPayoutAttemptPO po = new WithdrawalPayoutAttemptPO();
        po.setWithdrawalId(WITHDRAWAL_ID);
        po.setAttemptNo(1);
        po.setPayoutNo("PN-BYPASS");
        po.setChannel("MOCK");
        po.setState(1);
        po.setRequestedAt(NOW);

        assertThrows(DuplicateKeyException.class, () -> mapper.insert(po),
                "撞键必须抛出：静默重试会把「绕过锁」这个 bug 藏起来");
    }

    @Test
    @DisplayName("失败达上限 ⇒ 拒绝占号；CANCELLED 不计入上限")
    void shouldCapFailedAttemptsButIgnoreCancelled() throws SQLException {
        for (int i = 1; i <= 3; i++) {
            insertAttempt(i, "PN-F" + i, AttemptState.FAILED);
        }
        assertThrows(IllegalStateException.class,
                () -> repository.claimNewAttempt(WITHDRAWAL_ID, "PN-F4", "MOCK", NOW),
                "连续失败达上限后必须停手，否则就是无限重试器");

        execute("DELETE FROM withdrawal_payout_attempt");
        for (int i = 1; i <= 3; i++) {
            insertAttempt(i, "PN-C" + i, AttemptState.CANCELLED);
        }
        var attempt = repository.claimNewAttempt(WITHDRAWAL_ID, "PN-C4", "MOCK", NOW);
        assertEquals(4, attempt.attemptNo(), "撤销是主动终止，不该被算成失败次数");
    }

    @Test
    @DisplayName("updateState 条件更新：状态已变时 0 行，不覆盖")
    void shouldNotOverwrite_whenStateAlreadyChanged() {
        repository.claimNewAttempt(WITHDRAWAL_ID, "PN-1", "MOCK", NOW);

        assertEquals(1, repository.updateState("PN-1", AttemptState.REQUESTED, AttemptState.SUCCESS));
        assertEquals(0, repository.updateState("PN-1", AttemptState.REQUESTED, AttemptState.FAILED),
                "状态已变必须返回 0，不能把成功改成失败");
    }

    private boolean claimOrFalse(CyclicBarrier barrier, String payoutNo) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            repository.claimNewAttempt(WITHDRAWAL_ID, payoutNo, "MOCK", NOW);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void insertAttempt(int attemptNo, String payoutNo, AttemptState state) {
        WithdrawalPayoutAttemptPO po = new WithdrawalPayoutAttemptPO();
        po.setWithdrawalId(WITHDRAWAL_ID);
        po.setAttemptNo(attemptNo);
        po.setPayoutNo(payoutNo);
        po.setChannel("MOCK");
        po.setState(state.ordinal() + 1);
        po.setRequestedAt(NOW);
        mapper.insert(po);
    }

    private void execute(String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private int countAll() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM withdrawal_payout_attempt")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackages = "org.icedamericanomall.trade.infrastructure.persistence.finance")
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return new SimpleDriverDataSource(new Driver(),
                    "jdbc:h2:mem:payoutattempt;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(WithdrawalPayoutAttemptMapper.class);

            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setBanner(false);

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setGlobalConfig(globalConfig);
            return factoryBean.getObject();
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public WithdrawalPayoutAttemptRepository withdrawalPayoutAttemptRepository(
                WithdrawalPayoutAttemptMapper mapper) {
            return new WithdrawalPayoutAttemptRepositoryImpl(mapper);
        }
    }
}
