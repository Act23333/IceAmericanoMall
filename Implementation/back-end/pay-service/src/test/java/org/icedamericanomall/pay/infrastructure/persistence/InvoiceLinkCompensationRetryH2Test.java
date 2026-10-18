package org.icedamericanomall.pay.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.config.AuditFieldMetaObjectHandler;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkCompensationStatusEnum;
import org.icedamericanomall.pay.domain.enums.InvoiceLinkKindEnum;
import org.icedamericanomall.pay.domain.model.invoicelink.InvoiceLinkCompensation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补偿重试读侧 + 状态迁移的 H2 集成测试 —— 用<b>真实数据库条件</b>证明「条件更新」不是口头承诺。
 *
 * <p><b>为什么必须 H2 而不是只 mock Mapper</b>：本批的两条核心不变式都在 SQL 的 {@code WHERE} 里 ——
 * <ol>
 *   <li>拾取口径 {@code status = 0 AND next_retry_at <= now()} + {@code ORDER BY next_retry_at} +
 *       {@code LIMIT}（{@code idx_status_next_retry}）；</li>
 *   <li>状态迁移只在 {@code PENDING} 生效（重复调用第二次必须返回 {@code false}，
 *       且终态行的 {@code retry_count / next_retry_at} 不得被退避写回覆盖）。</li>
 * </ol>
 * Mock Mapper 的测试里，这两条都是「测试自己写的 thenReturn」——SQL 写错了照样绿。
 * 表结构与 {@code Initialize.sql}（{@code invoice_link_compensation}）逐字一致，
 * 装配方式与同目录 {@link InvoiceLinkCompensationH2Test} 相同（生产同款审计字段填充器 +
 * Spring 异常翻译器）。
 */
@DisplayName("InvoiceLinkCompensationRepositoryImpl H2 集成测试（到期拾取 + 条件状态迁移）")
class InvoiceLinkCompensationRetryH2Test {

    private static final String ORDER_NO = "bbbbbbbb000070008000000000000001";
    private static final String OTHER_ORDER_NO = "bbbbbbbb000070008000000000000002";

    private static final int PENDING = InvoiceLinkCompensationStatusEnum.PENDING.getCode();
    private static final int DONE = InvoiceLinkCompensationStatusEnum.DONE.getCode();
    private static final int FAILED_PERMANENT = InvoiceLinkCompensationStatusEnum.FAILED_PERMANENT.getCode();

    private InvoiceLinkCompensationRepositoryImpl repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:invoice_link_retry_" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE invoice_link_compensation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_no BINARY(16) NOT NULL,
                    scene_ref VARCHAR(64) NOT NULL,
                    kind TINYINT NOT NULL,
                    status TINYINT NOT NULL DEFAULT 0,
                    retry_count INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP NULL,
                    lease_until TIMESTAMP NULL,
                    last_error VARCHAR(500) NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_order_scene_kind UNIQUE (order_no, scene_ref, kind)
                )""");
        // 与被测 SQL 的筛选口径配套的索引（Initialize.sql 的 idx_status_next_retry）
        jdbcTemplate.execute("CREATE INDEX idx_status_next_retry "
                + "ON invoice_link_compensation (status, next_retry_at)");

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        globalConfig.setDbConfig(new GlobalConfig.DbConfig());
        globalConfig.setMetaObjectHandler(new AuditFieldMetaObjectHandler());
        GlobalConfigUtils.setGlobalConfig(config, globalConfig);
        config.addMapper(InvoiceLinkCompensationMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        repository = new InvoiceLinkCompensationRepositoryImpl(
                new SqlSessionTemplate(sqlSessionFactory).getMapper(InvoiceLinkCompensationMapper.class));
    }

    /** 直接落一行（可指定状态/重试次数/到期时间）—— 用于精确构造「已补偿」「未到期」等前置状态。 */
    private long insertRow(String orderNo, String sceneRef, InvoiceLinkKindEnum kind,
                           LocalDateTime nextRetryAt, int status, int retryCount, String lastError) {
        jdbcTemplate.update("INSERT INTO invoice_link_compensation "
                        + "(order_no, scene_ref, kind, status, retry_count, next_retry_at, last_error) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                IdGenerator.toBytes(orderNo), sceneRef, kind.getCode(), status, retryCount, nextRetryAt, lastError);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM invoice_link_compensation WHERE scene_ref = ? AND kind = ?",
                Long.class, sceneRef, kind.getCode());
        return id == null ? -1L : id;
    }

    private Map<String, Object> rowOf(long id) {
        return jdbcTemplate.queryForMap("SELECT status, retry_count, next_retry_at, last_error "
                + "FROM invoice_link_compensation WHERE id = ?", id);
    }

    private static int intOf(Object value) {
        return ((Number) value).intValue();
    }

    // ==================== 读侧：到期拾取 ====================

    @Test
    @DisplayName("到期拾取 — 只取 PENDING 且已到期，按 next_retry_at 升序；未到期行与已 DONE 行都不返回")
    void shouldPickOnlyDuePendingRowsInAscendingOrder_whenFindDueForRetry() {
        LocalDateTime now = LocalDateTime.now();
        long later = insertRow(ORDER_NO, "AS:2", InvoiceLinkKindEnum.VOID, now.minusMinutes(1), PENDING, 1, "早失败");
        long earlier = insertRow(OTHER_ORDER_NO, "AS:1", InvoiceLinkKindEnum.PARTIAL, now.minusMinutes(5), PENDING, 2, "更早失败");
        insertRow(ORDER_NO, "AS:3", InvoiceLinkKindEnum.VOID, now.plusHours(1), PENDING, 0, "未到退避点");
        insertRow(OTHER_ORDER_NO, "AS:4", InvoiceLinkKindEnum.VOID, now.minusMinutes(3), DONE, 0, null);

        List<InvoiceLinkCompensation> due = repository.findDueForRetry(100);

        assertEquals(2, due.size(), "未到期行与已 DONE 行都不得被拾取");
        assertEquals(earlier, due.get(0).getId(), "必须按 next_retry_at 升序（老单优先收敛）");
        assertEquals(later, due.get(1).getId());
        InvoiceLinkCompensation first = due.get(0);
        assertEquals(OTHER_ORDER_NO, first.getOrderNo(),
                "order_no 必须经 IdGenerator 还原成 32 位 hex 线格式（ADR-062），不得是 byte[] 的 toString");
        assertEquals("AS:1", first.getSceneRef());
        assertEquals(InvoiceLinkKindEnum.PARTIAL.getCode(), first.getKind());
        assertEquals(PENDING, first.getStatus());
        assertEquals(2, first.getRetryCount());
        assertEquals("更早失败", first.getLastError());
    }

    @Test
    @DisplayName("限量拾取 — LIMIT 生效：只返回最早的那一条（防一次拉爆）")
    void shouldRespectLimit_whenFindDueForRetry() {
        LocalDateTime now = LocalDateTime.now();
        long earliest = insertRow(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID, now.minusMinutes(9), PENDING, 0, null);
        insertRow(OTHER_ORDER_NO, "AS:2", InvoiceLinkKindEnum.VOID, now.minusMinutes(4), PENDING, 0, null);

        List<InvoiceLinkCompensation> due = repository.findDueForRetry(1);

        assertEquals(1, due.size());
        assertEquals(earliest, due.get(0).getId());
    }

    @Test
    @DisplayName("无到期行 — 返回空列表（不是 null），调度侧可直接 foreach")
    void shouldReturnEmptyList_whenNothingDue() {
        List<InvoiceLinkCompensation> due = repository.findDueForRetry(100);

        assertTrue(due.isEmpty());
    }

    // ==================== 状态迁移：条件更新 + 幂等 ====================

    @Test
    @DisplayName("置 DONE 的幂等 — 首次 true 且状态落 1；第二次 false（条件不命中），状态不变")
    void shouldReturnTrueThenFalse_whenMarkDoneTwice() {
        long id = insertRow(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID, LocalDateTime.now().minusMinutes(1),
                PENDING, 0, null);

        assertTrue(repository.markDone(id), "首次推进必须命中");
        assertEquals(DONE, intOf(rowOf(id).get("status")));

        assertFalse(repository.markDone(id), "重复推进必须返回 false（条件更新只从 PENDING 生效）");
        assertEquals(DONE, intOf(rowOf(id).get("status")), "终态不得被二次推进改写");
    }

    @Test
    @DisplayName("退避写入的幂等 — PENDING 时写入 retry_count/next_retry_at/last_error；已 DONE 时返回 false 且不覆盖")
    void shouldWriteBackoffOnlyWhenPending_whenScheduleRetry() {
        LocalDateTime due = LocalDateTime.now().minusMinutes(1);
        long pendingId = insertRow(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID, due, PENDING, 0, "首次失败");
        long doneId = insertRow(OTHER_ORDER_NO, "AS:2", InvoiceLinkKindEnum.VOID, due, DONE, 0, "先失败后成功");

        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(2);
        assertTrue(repository.scheduleRetry(pendingId, 1, nextRetryAt, "BizException[SYSTEM_BUSY] category=TRANSIENT: 超时"));

        Map<String, Object> row = rowOf(pendingId);
        assertEquals(1, intOf(row.get("retry_count")));
        assertEquals(PENDING, intOf(row.get("status")), "退避不得改变状态（仍待补偿）");
        assertTrue(((java.sql.Timestamp) row.get("next_retry_at")).toLocalDateTime()
                        .isAfter(LocalDateTime.now()),
                "next_retry_at 必须前移到未来（退避生效）: " + row.get("next_retry_at"));
        assertTrue(String.valueOf(row.get("last_error")).contains("category=TRANSIENT"));

        assertFalse(repository.scheduleRetry(doneId, 3, nextRetryAt, "迟到的退避"),
                "已 DONE 的行不得被退避写回待补偿");
        Map<String, Object> doneRow = rowOf(doneId);
        assertEquals(DONE, intOf(doneRow.get("status")));
        assertEquals(0, intOf(doneRow.get("retry_count")), "终态行的 retry_count 不得被覆盖");
        assertEquals("先失败后成功", doneRow.get("last_error"));
    }

    @Test
    @DisplayName("超长摘要 — last_error 落库前截断至列宽 500（VARCHAR(500)），落库本身不因摘要过长失败")
    void shouldTruncateLastError_whenRetrySummaryTooLong() {
        long id = insertRow(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID, LocalDateTime.now().minusMinutes(1),
                PENDING, 0, null);

        repository.scheduleRetry(id, 1, LocalDateTime.now().plusMinutes(2), "x".repeat(800));

        assertEquals(InvoiceLinkCompensation.LAST_ERROR_MAX_LENGTH,
                String.valueOf(rowOf(id).get("last_error")).length());
    }

    @Test
    @DisplayName("置永久失败的幂等 — 首次 true 且状态落 2，last_error 落摘要；第二次 false")
    void shouldMarkFailedPermanentOnce_whenPending() {
        long id = insertRow(ORDER_NO, "AS:1", InvoiceLinkKindEnum.PARTIAL, LocalDateTime.now().minusMinutes(1),
                PENDING, 0, null);

        assertTrue(repository.markFailedPermanent(id, "UpstreamException category=CLIENT: 订单不存在"));
        Map<String, Object> row = rowOf(id);
        assertEquals(FAILED_PERMANENT, intOf(row.get("status")));
        assertTrue(String.valueOf(row.get("last_error")).contains("CLIENT"));

        assertFalse(repository.markFailedPermanent(id, "再次置失败"));
        assertEquals(FAILED_PERMANENT, intOf(rowOf(id).get("status")));
        assertTrue(String.valueOf(rowOf(id).get("last_error")).contains("CLIENT"),
                "终态摘要不得被二次调用覆盖");
    }
}
