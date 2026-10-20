package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository.RawShard;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.DiffType;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Item;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.MatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A 批次：在**真 Spring 事务**下验证「失败即整体回滚」。
 *
 * <h2>为什么单独一个用例类</h2>
 * 既有的 {@code SettlementFundAccountH2Test} 风格 harness 没有事务管理器，
 * 实测 {@code SqlSession.rollback()} 与 {@code getConnection().rollback()} 都不生效
 * （open-items §1.77）⇒ 它无法验证任何回滚语义。
 * 这里用 {@code @SpringJUnitConfig} + {@code DataSourceTransactionManager} 让
 * {@code ReconciliationRepositoryImpl} 上的 {@code @Transactional} **真的由 Spring 代理生效**，
 * 从而首次证明「生产上会不会真回滚」。
 *
 * <h2>断言在同一事务之外</h2>
 * 断言一律用**另开连接**查（{@link #countItems}/{@link #batchStatus}），
 * 看到的是**已提交**状态 —— 这样"回滚"与否是客观事实，不是会话内的视图。
 */
@SpringJUnitConfig(ReconciliationTransactionTest.TestConfig.class)
@DisplayName("对账仓储 · Spring 真事务（失败即回滚）")
class ReconciliationTransactionTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    @Autowired
    private ReconciliationRepository repository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void createSchemaIfAbsent() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reconciliation_batch (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(64) NOT NULL,
                    provider VARCHAR(32) NOT NULL,
                    biz_date DATE NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    fetched_at TIMESTAMP,
                    matched_count INT NOT NULL DEFAULT 0,
                    diff_count INT NOT NULL DEFAULT 0,
                    resolved_at TIMESTAMP,
                    resolved_by BIGINT,
                    resolve_note VARCHAR(500),
                    superseded_by VARCHAR(64),
                    active_key VARCHAR(160) GENERATED ALWAYS AS (
                        CASE WHEN superseded_by IS NULL THEN '0' ELSE CONCAT(superseded_by, '#', batch_no) END
                    ),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_batch_no UNIQUE (batch_no),
                    CONSTRAINT uk_active_batch UNIQUE (provider, biz_date, active_key)
                )""");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reconciliation_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(64) NOT NULL,
                    payout_no VARCHAR(64) NOT NULL,
                    channel_ref VARCHAR(160) NOT NULL,
                    ours_amount INT,
                    channel_amount INT,
                    biz_date DATE,
                    match_type TINYINT NOT NULL,
                    diff_type VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_batch_item UNIQUE (batch_no, payout_no, channel_ref)
                )""");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS reconciliation_raw (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(64) NOT NULL,
                    seq INT NOT NULL,
                    payload BLOB NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_batch_seq UNIQUE (batch_no, seq)
                )""");
        }
    }

    @Test
    @DisplayName("插入失败 ⇒ 整体回滚：旧逐笔还在、批次状态退回 PENDING（事务未被切开）")
    void shouldRollbackEntireRefetch_whenInsertFails() throws SQLException {
        seedBatch("TX-ROLLBACK", "TX-R1");
        seedItem("TX-R1", "WD-OLD", "CH-OLD");
        assertEquals(1, countItems("TX-R1"), "前置：已提交一条旧逐笔");
        assertEquals(1, batchStatus("TX-R1"), "前置：批次为 PENDING");

        String tooLong = "X".repeat(200);
        assertThrows(RuntimeException.class, () -> repository.refetch("TX-R1",
                List.of(new Item("WD-NEW", tooLong, 5_000, 5_000, DAY, MatchType.EXACT, DiffType.NONE)),
                List.of(new RawShard(1, new byte[] {9}))));

        assertEquals(1, countItems("TX-R1"), "回滚后旧逐笔必须还在（删除被撤销）");
        assertEquals(0, countOf("SELECT COUNT(*) FROM reconciliation_item WHERE batch_no = ? AND payout_no = 'WD-NEW'",
                "TX-R1"), "失败的那条不得存在");
        assertEquals(1, batchStatus("TX-R1"),
                "批次状态必须退回 PENDING：若 refetch 内的 claim 用了独立事务，这里会是 FETCHED");
    }

    @Test
    @DisplayName("成功路径 ⇒ 真的提交：新逐笔与 FETCHED 对独立连接可见")
    void shouldCommit_whenRefetchSucceeds() throws SQLException {
        seedBatch("TX-COMMIT", "TX-C1");

        repository.refetch("TX-C1",
                List.of(new Item("WD-1", "CH-1", 4_000, 4_000, DAY, MatchType.EXACT, DiffType.NONE)),
                List.of(new RawShard(1, new byte[] {1})));

        assertEquals(1, countItems("TX-C1"), "成功路径必须提交");
        assertEquals(2, batchStatus("TX-C1"), "批次应推进到 FETCHED 并提交");
    }

    @Test
    @DisplayName("事务模板内抛出 ⇒ 回滚（证明事务管理器真的接了代理，不是空转）")
    void shouldRollbackInsideTransactionTemplate() throws SQLException {
        seedBatch("TX-TEMPLATE", "TX-T1");

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            insertItemInCurrentTransaction("TX-T1", "WD-TMP", "CH-TMP");
            throw new IllegalStateException("故意失败，验证回滚");
        }));

        assertEquals(0, countItems("TX-T1"), "模板内写入必须被回滚");
    }

    // ─────────────────────────── 数据准备与查询（独立连接，看已提交状态） ───────────────────────────

    private void seedBatch(String provider, String batchNo) throws SQLException {
        execute("INSERT INTO reconciliation_batch (batch_no, provider, biz_date, status) VALUES (?, ?, ?, 1)",
                batchNo, provider, DAY);
    }

    private void seedItem(String batchNo, String payoutNo, String channelRef) throws SQLException {
        execute("INSERT INTO reconciliation_item (batch_no, payout_no, channel_ref, match_type) VALUES (?, ?, ?, 1)",
                batchNo, payoutNo, channelRef);
    }

    /**
     * 在**事务内**的写入必须用事务绑定的连接（{@code DataSourceUtils.getConnection}）。
     *
     * <p>踩过的坑：这里最初直接调 {@code execute(...)} 新开连接写 —— 那是**自动提交**的独立事务，
     * 事务模板回滚与它无关，于是「模板内写入被回滚」的断言永远红。这不是测试框架的问题，是写法的问题。
     */
    private void insertItemInCurrentTransaction(String batchNo, String payoutNo, String channelRef) {
        Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO reconciliation_item (batch_no, payout_no, channel_ref, match_type) VALUES (?, ?, ?, 1)")) {
            ps.setString(1, batchNo);
            ps.setString(2, payoutNo);
            ps.setString(3, channelRef);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void execute(String sql, Object... args) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private int countItems(String batchNo) throws SQLException {
        return countOf("SELECT COUNT(*) FROM reconciliation_item WHERE batch_no = ?", batchNo);
    }

    private int batchStatus(String batchNo) throws SQLException {
        return countOf("SELECT status FROM reconciliation_batch WHERE batch_no = ?", batchNo);
    }

    private int countOf(String sql, Object... args) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ─────────────────────────── 测试装配 ───────────────────────────

    /**
     * 只装配被测仓储需要的东西：H2 数据源、MyBatis-Plus 会话工厂、**真事务管理器**。
     * 不启动 Spring Boot 应用（不碰 Nacos/MySQL/Feign），保持用例快且确定。
     */
    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackages = "org.icedamericanomall.trade.infrastructure.persistence.reconciliation")
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return new SimpleDriverDataSource(new Driver(),
                    "jdbc:h2:mem:reconciliationtx;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(ReconciliationBatchMapper.class);
            configuration.addMapper(ReconciliationItemMapper.class);
            configuration.addMapper(ReconciliationRawMapper.class);

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
        public TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        /** 返回接口类型：Spring 用 JDK 动态代理由此暴露事务语义，注入也按接口来。 */
        @Bean
        public ReconciliationRepository reconciliationRepository(ReconciliationBatchMapper batchMapper,
                                                                ReconciliationItemMapper itemMapper,
                                                                ReconciliationRawMapper rawMapper) {
            return new ReconciliationRepositoryImpl(batchMapper, itemMapper, rawMapper);
        }
    }
}
