package org.icedamericanomall.trade.infrastructure.persistence.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 出款停留告警的「真库去重」用例。
 *
 * <h2>为什么必须上真库</h2>
 * mock 桩只能证明"调了方法"，证明不了 **`INSERT IGNORE` + 唯一键** 真的把重投挡在外面。
 * 告警是每分钟级的任务，重复触发会让值班被淹 —— 这条去重是它的唯一保证。
 *
 * <h2>顺带守住 E1 的清理 SQL</h2>
 * 本用例的 DDL 抄自 {@code Initialize.sql}（列名 `created_at`，不是 `create_time`），
 * 因此清理语句的列名如果写错，这里会当场红 —— 这正是 E1 刚踩过的坑。
 */
@SpringJUnitConfig(PayoutAlertLogDedupeTest.TestConfig.class)
@DisplayName("告警去重账（H2 真库）")
class PayoutAlertLogDedupeTest {

    @Autowired
    private WithdrawalMapper withdrawalMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void createSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS payout_alert_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    payout_no VARCHAR(64) NOT NULL,
                    alert_gen INT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_payout_alert UNIQUE (payout_no, alert_gen)
                )""");
            // 顺序：先建表再清空 —— 反过来会在首跑直接炸（建表语句还没执行）
            stmt.execute("DELETE FROM payout_alert_log");
        }
    }

    @Test
    @DisplayName("同一单同一代次只记一次：第二次插入影响行数为零")
    void shouldRecordOnlyOnce_whenSameGenerationRepeats() {
        assertEquals(1, withdrawalMapper.markAlertedOnce("PO-1", 0), "首次必须记下");
        assertEquals(0, withdrawalMapper.markAlertedOnce("PO-1", 0),
                "重复触发必须被唯一键挡掉：否则值班会被同一件事反复打扰");
        assertEquals(1, countAll(), "账上只能有一条");
    }

    @Test
    @DisplayName("代次推进或换了单据 ⇒ 视为新事件，可以再记")
    void shouldRecordAgain_whenGenerationAdvancesOrPayoutDiffers() {
        assertEquals(1, withdrawalMapper.markAlertedOnce("PO-2", 0));
        assertEquals(1, withdrawalMapper.markAlertedOnce("PO-2", 1), "停留时间翻代，属于新事件");
        assertEquals(1, withdrawalMapper.markAlertedOnce("PO-3", 0), "不同单据互不干扰");
        assertEquals(3, countAll());
    }

    @Test
    @DisplayName("保留期清理按 created_at 删：旧记录消失、新记录留下")
    void shouldPurgeOnlyExpiredRows() throws SQLException {
        insertWithCreatedAt("PO-OLD", 0, LocalDateTime.of(2025, 1, 1, 0, 0));
        insertWithCreatedAt("PO-NEW", 0, LocalDateTime.of(2026, 10, 1, 0, 0));
        assertEquals(2, countAll());

        int deleted = withdrawalMapper.deleteAlertLogBefore(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertEquals(1, deleted, "只删早于边界的");
        assertEquals(0, countOf("PO-OLD"), "旧记录必须被删");
        assertEquals(1, countOf("PO-NEW"), "新记录必须留下");
    }

    // ─────────────────────────── 辅助 ───────────────────────────

    private void insertWithCreatedAt(String payoutNo, int gen, LocalDateTime createdAt) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO payout_alert_log(payout_no, alert_gen, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, payoutNo);
            ps.setInt(2, gen);
            ps.setObject(3, createdAt);
            ps.executeUpdate();
        }
    }

    private int countAll() {
        return countOf(null);
    }

    private int countOf(String payoutNo) {
        String sql = payoutNo == null
                ? "SELECT COUNT(*) FROM payout_alert_log"
                : "SELECT COUNT(*) FROM payout_alert_log WHERE payout_no = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (payoutNo != null) {
                ps.setString(1, payoutNo);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration
    @MapperScan(basePackages = "org.icedamericanomall.trade.infrastructure.persistence.finance")
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return new SimpleDriverDataSource(new Driver(),
                    "jdbc:h2:mem:payoutalert;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(WithdrawalMapper.class);

            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setBanner(false);

            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setConfiguration(configuration);
            factoryBean.setGlobalConfig(globalConfig);
            return factoryBean.getObject();
        }
    }
}
