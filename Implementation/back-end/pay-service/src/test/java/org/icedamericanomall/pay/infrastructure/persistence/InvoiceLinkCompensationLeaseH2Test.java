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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认领租约（{@code lease_until}）的 H2 集成测试 —— 用<b>真实数据库条件</b>证明「同一行只会被一个执行者重调」。
 *
 * <p><b>为什么必须 H2 而不是 mock Mapper</b>：互斥的唯一执行者是认领 SQL 的 {@code WHERE}
 * （{@code status = PENDING AND (lease_until IS NULL OR lease_until < now)}）+ 数据库行锁。
 * mock 掉 Mapper 后，「第二次认领必须失败」就变成测试自己写的 {@code thenReturn(false)} ——
 * SQL 里漏掉租约条件（甚至整个 {@code WHERE} 写错）照样绿。
 *
 * <p><b>本类要证明的四件事</b>（对应「用会自愈的时间盒代替会卡住的状态」这条设计）：
 * <ol>
 *   <li>同一行两次认领，只有第一次成功（互斥）；</li>
 *   <li>有效租约内的行<b>不会</b>被 {@code findDueForRetry} 拾取（不会被另一执行者重复重调）；</li>
 *   <li>租约过期后该行<b>自动</b>重新可拾取、可再认领（= 回收不靠任务，靠时间）；</li>
 *   <li>三条状态迁移之后 {@code lease_until} 一律回到 {@code NULL}（终态/退避行不携带无用租约）。</li>
 * </ol>
 *
 * <p>表结构与 {@code Initialize.sql}（{@code invoice_link_compensation}）逐字一致；
 * 装配方式与同目录 {@link InvoiceLinkCompensationRetryH2Test} 相同（生产同款审计字段填充器 +
 * Spring 异常翻译器）。
 */
@DisplayName("InvoiceLinkCompensationRepositoryImpl H2 集成测试（认领租约 lease_until）")
class InvoiceLinkCompensationLeaseH2Test {

    private static final String ORDER_NO = "bbbbbbbb000070008000000000000001";
    private static final String OTHER_ORDER_NO = "bbbbbbbb000070008000000000000002";

    /** 租约时长：与 {@code InvoiceLinkCompensationRetryManager#CLAIM_LEASE_SECONDS} 同值（包级常量，跨包引用不做）。 */
    private static final int LEASE_SECONDS = 120;

    private static final int PENDING = InvoiceLinkCompensationStatusEnum.PENDING.getCode();
    private static final int DONE = InvoiceLinkCompensationStatusEnum.DONE.getCode();
    private static final int FAILED_PERMANENT = InvoiceLinkCompensationStatusEnum.FAILED_PERMANENT.getCode();

    private InvoiceLinkCompensationRepositoryImpl repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:invoice_link_lease_" + System.nanoTime()
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

    /** 落一行「已到期、未认领」的 PENDING 记录，返回主键。 */
    private long insertDuePending(String orderNo, String sceneRef, InvoiceLinkKindEnum kind) {
        jdbcTemplate.update("INSERT INTO invoice_link_compensation "
                        + "(order_no, scene_ref, kind, status, retry_count, next_retry_at, lease_until, last_error) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?)",
                IdGenerator.toBytes(orderNo), sceneRef, kind.getCode(), PENDING, 0,
                LocalDateTime.now().minusMinutes(1), "上次失败");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM invoice_link_compensation WHERE scene_ref = ? AND kind = ?",
                Long.class, sceneRef, kind.getCode());
        return id == null ? -1L : id;
    }

    /** 读出该行 {@code lease_until}（{@code null} 表示未认领 / 租约已释放）。 */
    private LocalDateTime leaseOf(long id) {
        Timestamp lease = jdbcTemplate.queryForObject(
                "SELECT lease_until FROM invoice_link_compensation WHERE id = ?", Timestamp.class, id);
        return lease == null ? null : lease.toLocalDateTime();
    }

    /** 直接改写租约到期时间（模拟「时间流逝到租约之后」，不依赖 sleep）。 */
    private void forceLease(long id, LocalDateTime leaseUntil) {
        jdbcTemplate.update("UPDATE invoice_link_compensation SET lease_until = ? WHERE id = ?", leaseUntil, id);
    }

    /** 读出该行状态码。 */
    private int statusOf(long id) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM invoice_link_compensation WHERE id = ?", Integer.class, id);
        return status == null ? -1 : status;
    }

    // ==================== ① 认领互斥 ====================

    @Test
    @DisplayName("认领互斥 — 同一行两次 tryClaim：只有第一次 true（受影响行数 = 1 才算认领成功），租约落在未来")
    void shouldClaimOnlyOnce_whenTwoExecutorsClaimSameRow() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        assertNull(leaseOf(id), "落库即未认领：lease_until 必须为 NULL");

        LocalDateTime before = LocalDateTime.now();
        boolean first = repository.tryClaim(id, LEASE_SECONDS);
        boolean second = repository.tryClaim(id, LEASE_SECONDS);

        assertTrue(first, "第一次认领必须成功（该行 PENDING 且无有效租约）");
        assertFalse(second, "第二次必须失败：有效租约已存在，受影响行数 = 0 → 跳过该行（非错误）");
        LocalDateTime lease = leaseOf(id);
        assertNotNull(lease, "认领成功必须写入 lease_until");
        // 1 秒容差：库列（TIMESTAMP 微秒精度）会截断纳秒，且「取当前时间」本身有微妙级抖动；
        // 容差只吸收时钟精度，不放松被验证的语义（分钟级租约 vs 零租约 vs 固定值仍能区分）
        assertTrue(lease.isAfter(before.plusSeconds(LEASE_SECONDS - 1)),
                "租约到期时间必须约为「认领时刻 + 租约时长」，实际: " + lease);
        assertFalse(lease.isAfter(LocalDateTime.now().plusSeconds(LEASE_SECONDS + 1)),
                "租约不得明显超出认领时刻 + 租约时长（否则等于永不回收），实际: " + lease);
    }

    @Test
    @DisplayName("终态行不可认领 — 已 DONE 的行 tryClaim 返回 false（认领不得把终态行拉回待处理）")
    void shouldNotClaim_whenRowNotPending() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        jdbcTemplate.update("UPDATE invoice_link_compensation SET status = ? WHERE id = ?", DONE, id);

        assertFalse(repository.tryClaim(id, LEASE_SECONDS), "只认领 PENDING 行");
        assertNull(leaseOf(id), "未认领成功就不得留下租约");
    }

    // ==================== ② 有效租约内的行不被拾取 ====================

    @Test
    @DisplayName("租约未到期 — 被认领（租约有效）的到期行不会被 findDueForRetry 拾取，未认领的同批行照常拾取")
    void shouldNotPickUpRow_whenLeaseActive() {
        long claimed = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        long free = insertDuePending(OTHER_ORDER_NO, "AS:2", InvoiceLinkKindEnum.PARTIAL);
        assertTrue(repository.tryClaim(claimed, LEASE_SECONDS));

        List<InvoiceLinkCompensation> due = repository.findDueForRetry(100);

        assertEquals(1, due.size(), "持有有效租约的行不得进入本轮可认领集合（否则就是重复重调）");
        assertEquals(free, due.get(0).getId());
    }

    // ==================== ③ 租约过期后自动可拾取（= 回收不靠任务） ====================

    @Test
    @DisplayName("租约过期 — 把 lease_until 写成过去时间后，该行重新出现在 findDueForRetry 且可被再次认领")
    void shouldPickUpAndReclaimRow_whenLeaseExpired() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        assertTrue(repository.tryClaim(id, LEASE_SECONDS));
        assertTrue(repository.findDueForRetry(100).isEmpty(), "租约有效期间不得被拾取");

        // 模拟时间流逝越过租约（不 sleep：直接把到期时间推到过去）—— 没有回收任务，回收靠时间本身
        forceLease(id, LocalDateTime.now().minusSeconds(1));

        List<InvoiceLinkCompensation> due = repository.findDueForRetry(100);
        assertEquals(1, due.size(), "租约过期后该行必须自动回到可认领集合（无需回收任务）");
        assertEquals(id, due.get(0).getId());
        assertTrue(repository.tryClaim(id, LEASE_SECONDS), "过期租约必须可被再次认领（持有者崩溃后的自愈路径）");
        assertTrue(leaseOf(id).isAfter(LocalDateTime.now()), "再次认领必须刷新租约到未来");
    }

    // ==================== ④ 三条状态迁移都释放租约 ====================

    @Test
    @DisplayName("置 DONE — 状态推进后 lease_until 置回 NULL（终态行不携带无用租约）")
    void shouldClearLease_whenMarkDone() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        assertTrue(repository.tryClaim(id, LEASE_SECONDS));

        assertTrue(repository.markDone(id));

        assertNull(leaseOf(id), "markDone 必须释放租约");
    }

    @Test
    @DisplayName("退避写入 — retry_count/next_retry_at 落库后 lease_until 置回 NULL（否则会白等一个租约周期）")
    void shouldClearLease_whenScheduleRetry() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.VOID);
        assertTrue(repository.tryClaim(id, LEASE_SECONDS));

        assertTrue(repository.scheduleRetry(id, 1, LocalDateTime.now().plusMinutes(2), "category=TRANSIENT: 超时"));

        assertNull(leaseOf(id), "scheduleRetry 必须释放租约");
        assertEquals(PENDING, statusOf(id), "退避不得改变状态（仍待补偿）");
    }

    @Test
    @DisplayName("置永久失败 — 状态推进后 lease_until 置回 NULL（人工排查时租约列不得给出「仍有人在跑」的假信号）")
    void shouldClearLease_whenMarkFailedPermanent() {
        long id = insertDuePending(ORDER_NO, "AS:1", InvoiceLinkKindEnum.PARTIAL);
        assertTrue(repository.tryClaim(id, LEASE_SECONDS));

        assertTrue(repository.markFailedPermanent(id, "UpstreamException category=CLIENT: 订单不存在"));

        assertNull(leaseOf(id), "markFailedPermanent 必须释放租约");
        assertEquals(FAILED_PERMANENT, statusOf(id));
    }
}
