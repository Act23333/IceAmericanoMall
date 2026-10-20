package org.icedamericanomall.trade.infrastructure.persistence.reconciliation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.h2.Driver;
import org.icedamericanomall.trade.domain.enums.ReconciliationStatus;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository;
import org.icedamericanomall.trade.domain.repository.ReconciliationRepository.RawShard;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.DiffType;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.Item;
import org.icedamericanomall.trade.domain.service.WithdrawalReconciliationComparer.MatchType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对账仓储的 H2 集成验证（真库、真 SQL）—— 只验不依赖事务回滚的三条：
 * 重拉不翻倍（走 FETCHED 分支）、三态拒重拉、claim 串行等价。
 *
 * <h2>为什么不含"失败不留半截"</h2>
 * 本 harness 实测无法验证回滚（SqlSession.rollback() 与 getConnection().rollback() 都无效，
 * 见 open-items 1.77）⇒ 回滚与失败路径必须上 @SpringBootTest + DataSourceTransactionManager，
 * 属独立批次。此处不造替身。
 *
 * <h2>为什么 rebind() 放在"操作边界"</h2>
 * 三条用例都依赖"两次操作之间状态可见"：第一次 refetch/claim 提交后，
 * 第二次必须在新 session 里看到已提交状态。若只在测试方法开头 rebind 一次，
 * 第二次可能读到未提交视图 ⇒ 红在装配、不是逻辑。
 *
 * <h2>用例间隔离</h2>
 * 用 provider/账期数据隔离；三态用例各自造批次，不共用一个再改状态。
 */
@DisplayName("对账仓储（H2 真库，仅成功路径）")
class ReconciliationRepositoryH2Test {

    private static final String PROVIDER_REFETCH = "H2-REFETCH";
    private static final String PROVIDER_CLAIM = "H2-CLAIM";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    private static SimpleDriverDataSource dataSource;
    private static SqlSession session;
    private static ReconciliationBatchMapper batchMapper;
    private static ReconciliationItemMapper itemMapper;
    private static ReconciliationRawMapper rawMapper;
    private static ReconciliationRepository repository;

    @BeforeAll
    static void setUp() throws SQLException {
        dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:reconciliationdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE reconciliation_batch (
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
                CREATE TABLE reconciliation_item (
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
                CREATE TABLE reconciliation_raw (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    batch_no VARCHAR(64) NOT NULL,
                    seq INT NOT NULL,
                    payload BLOB NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_batch_seq UNIQUE (batch_no, seq)
                )""");
        }
        rebind();
    }

    @AfterAll
    static void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    @DisplayName("重拉不翻倍：第二次走 FETCHED 分支（不调 claim、直接删后插）")
    void shouldNotDuplicateItems_whenRefetchTwice() {
        seedBatch(PROVIDER_REFETCH, "R-1");

        List<Item> items = List.of(
                new Item("WD-1", "CH-1", 4_000, 4_000, DAY, MatchType.EXACT, DiffType.NONE),
                new Item("WD-2", null, 5_000, null, DAY, MatchType.DIFF, DiffType.OURS_ONLY));

        inTx(() -> {
            repository.refetch("R-1", items, List.of(new RawShard(1, new byte[] {1})));
            return null;
        });
        rebind();

        assertEquals(2, countItems("R-1"), "首次拉取落两条");
        inTx(() -> {
            repository.refetch("R-1", items, List.of(new RawShard(1, new byte[] {2})));
            return null;
        });
        rebind();

        assertEquals(2, countItems("R-1"), "重拉必须不翻倍（先删后插）");
        assertEquals(1, queryInt("SELECT COUNT(*) FROM reconciliation_raw WHERE batch_no = ?", "R-1"),
                "分片同样不叠加");
        assertEquals("__NULL__", queryString(
                        "SELECT channel_ref FROM reconciliation_item WHERE batch_no = ? AND payout_no = 'WD-2'", "R-1"),
                "空值落库前编码成哨兵：列 NOT NULL，且 NULL 不参与唯一键去重");
    }

    @Test
    @DisplayName("三态（MATCHED/DIFF/RESOLVED）各自拒绝重拉：各自造批次，不共用再改状态")
    void shouldRejectRefetch_whenClosed() {
        String[][] targets = {{"H2-MATCHED", "MATCHED"}, {"H2-DIFF", "DIFF"}, {"H2-RESOLVED", "RESOLVED"}};
        for (String[] target : targets) {
            String provider = target[0];
            String batchNo = provider + "-1";
            seedBatch(provider, batchNo);
            closeBatch(batchNo, target[1]);

            assertThrows(ReconciliationRepository.NotRefetchableException.class,
                    () -> inTx(() -> {
                        repository.refetch(batchNo, List.of(), List.of());
                        return null;
                    }), provider + " 已闭环仍允许重拉");
            rebind();
        }
    }

    @Test
    @DisplayName("claim 串行等价：第一次提交后，第二次在新 session 拿 0 行并抛 ConcurrentClaim")
    void shouldThrowConcurrentClaim_whenSecondClaimSeesCommittedState() {
        seedBatch(PROVIDER_CLAIM, "C-1");

        inTx(() -> {
            repository.claimBatch("C-1", ReconciliationStatus.PENDING, ReconciliationStatus.FETCHED);
            return null;
        });
        rebind();

        assertThrows(ReconciliationRepository.ConcurrentClaimException.class,
                () -> inTx(() -> {
                    repository.claimBatch("C-1", ReconciliationStatus.PENDING, ReconciliationStatus.FETCHED);
                    return null;
                }));
        rebind();
    }

    @Test
    @DisplayName("无批次 ⇒ Optional.empty；有批次 ⇒ 命中且生成列 active_key 为字符串 0")
    void shouldFindActiveBatchOnly() {
        assertTrue(repository.findActiveBatch("H2-NONE", DAY).isEmpty(), "无批次是 empty，不是异常");

        seedBatch("H2-ACTIVE", "A-1");
        rebind();

        assertTrue(repository.findActiveBatch("H2-ACTIVE", DAY).isPresent(), "有批次必须命中");
        assertEquals(1, queryInt(
                        "SELECT COUNT(*) FROM reconciliation_batch WHERE provider = ? AND active_key = '0'",
                        "H2-ACTIVE"),
                "生成列按 CASE WHEN 算出字符串 0");
    }

    // ─────────────────────────── harness ───────────────────────────

    /**
     * 幂等重绑：关闭旧 session → 开新 session → 重取 mapper → 重建仓储。
     *
     * <p>调用时机是每次操作之后（操作边界），不是每个测试方法开头 ——
     * 三条用例都依赖"下一次操作看到已提交状态"。
     */
    private static void rebind() {
        if (session != null) {
            session.close();
        }
        try {
            MybatisConfiguration config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(ReconciliationBatchMapper.class);
            config.addMapper(ReconciliationItemMapper.class);
            config.addMapper(ReconciliationRawMapper.class);

            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setBanner(false);

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(config);
            factoryBean.setGlobalConfig(globalConfig);

            session = factoryBean.getObject().openSession(false);
        } catch (Exception e) {
            throw new IllegalStateException("H2 session 装配失败", e);
        }
        batchMapper = session.getMapper(ReconciliationBatchMapper.class);
        itemMapper = session.getMapper(ReconciliationItemMapper.class);
        rawMapper = session.getMapper(ReconciliationRawMapper.class);
        repository = new ReconciliationRepositoryImpl(batchMapper, itemMapper, rawMapper);
    }

    private static <T> T inTx(Supplier<T> op) {
        try {
            T result = op.get();
            session.commit();
            return result;
        } catch (RuntimeException e) {
            session.rollback();
            throw e;
        }
    }

    private static void seedBatch(String provider, String batchNo) {
        inTx(() -> {
            ReconciliationBatchPO po = new ReconciliationBatchPO();
            po.setBatchNo(batchNo);
            po.setProvider(provider);
            po.setBizDate(DAY);
            po.setStatus(ReconciliationStatus.PENDING.getCode());
            po.setMatchedCount(0);
            po.setDiffCount(0);
            batchMapper.insert(po);
            return null;
        });
        rebind();
    }

    /** 把批次推进到目标闭环态，全程走白名单。 */
    private static void closeBatch(String batchNo, String finalState) {
        inTx(() -> {
            batchMapper.claimStatus(batchNo, ReconciliationStatus.PENDING.getCode(),
                    ReconciliationStatus.FETCHED.getCode());
            int fetched = ReconciliationStatus.FETCHED.getCode();
            if ("MATCHED".equals(finalState) || "DIFF".equals(finalState)) {
                batchMapper.claimStatus(batchNo, fetched, "MATCHED".equals(finalState)
                        ? ReconciliationStatus.MATCHED.getCode() : ReconciliationStatus.DIFF.getCode());
            } else {
                batchMapper.claimStatus(batchNo, fetched, ReconciliationStatus.DIFF.getCode());
                batchMapper.claimStatus(batchNo, ReconciliationStatus.DIFF.getCode(),
                        ReconciliationStatus.RESOLVED.getCode());
            }
            return null;
        });
        rebind();
    }

    private static int countItems(String batchNo) {
        return queryInt("SELECT COUNT(*) FROM reconciliation_item WHERE batch_no = ?", batchNo);
    }

    /** 独立连接查询：只依赖已提交数据，与服务 session 无关。 */
    private static int queryInt(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String queryString(String sql, Object... args) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
