package org.icedamericanomall.trade.app.finance;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.trade.adapter.assembler.TradeConverterImpl;
import org.icedamericanomall.trade.adapter.request.WithdrawalApplyReq;
import org.icedamericanomall.trade.domain.enums.SettlementStatusEnum;
import org.icedamericanomall.trade.domain.enums.WithdrawalStatusEnum;
import org.icedamericanomall.trade.domain.model.finance.SellerFundAccount;
import org.icedamericanomall.trade.domain.model.finance.Settlement;
import org.icedamericanomall.trade.domain.model.finance.Withdrawal;
import org.icedamericanomall.trade.domain.repository.SellerFundAccountRepository;
import org.icedamericanomall.trade.domain.repository.SettlementRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalRepository;
import org.icedamericanomall.trade.domain.repository.WithdrawalSettlementRepository;
import org.icedamericanomall.trade.infrastructure.persistence.finance.SellerFundAccountMapper;
import org.icedamericanomall.trade.infrastructure.persistence.finance.SellerFundAccountRepositoryImpl;
import org.icedamericanomall.trade.infrastructure.persistence.finance.SettlementMapper;
import org.icedamericanomall.trade.infrastructure.persistence.finance.SettlementRepositoryImpl;
import org.icedamericanomall.trade.infrastructure.persistence.finance.WithdrawalMapper;
import org.icedamericanomall.trade.infrastructure.persistence.finance.WithdrawalRepositoryImpl;
import org.icedamericanomall.trade.infrastructure.persistence.finance.WithdrawalSettlementMapper;
import org.icedamericanomall.trade.infrastructure.persistence.finance.WithdrawalSettlementRepositoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 商家资金账户 H2 集成测试 —— <b>T-WITHDRAW 第一步的验收用例</b>。
 *
 * <p><b>为什么必须是 H2 真库、而不是 mock</b>：本批要验的四件事全都发生在「SQL 谓词 + 影响行数」上 ——
 * 条件迁移（{@code status / period_end / settlement_amount / order_count} 四个条件）、账户累加、
 * 审核的条件迁移 + 记账、以及两条对账等式。用 Mockito 只能验到「方法被调用了」，
 * 恰恰验不到「谓词写对没有」。
 *
 * <p><b>H2 schema 是「手写子集」，不是 {@code Initialize.sql}</b>：结构真源是
 * {@code Implementation/back-end/database/Initialize.sql}，本用例三张表的 DDL 是**手工复制的子集**
 * （H2 跑不了 MySQL 专有子句）。因此：
 * <ul>
 *   <li>任何列 / 类型 / 唯一键变更（{@code settlement} / {@code seller_fund_account} / {@code withdrawal}）
 *       <b>必须同步本文件的 DDL</b>；</li>
 *   <li><b>不只靠「记得同步」</b>：{@link #assertSchemaMatchesProduction()} 在 {@code @BeforeAll} 里读
 *       {@code information_schema} 断言关键列与唯一键确实存在 —— 手写 DDL 漏列会**当场红**，
 *       而不是等到某条用例在「结构与生产不一致」的前提下给出假绿/假红；</li>
 *   <li>已知的**刻意差异**（列出来是为防止被当成漏写）：① 时间列允许 NULL、且不写
 *       {@code ON UPDATE CURRENT_TIMESTAMP}（本用例不读 {@code update_time}）；② 省略 {@code ENGINE} /
 *       {@code COLLATE} / 全部 {@code idx_*} 二级索引；③ 唯一键用表级 {@code CONSTRAINT ... UNIQUE} 写法
 *       （H2 与 MySQL 都接受）。判据：**行为相关的差异一条都不许有**（列名、类型、唯一键必须与生产一致）。</li>
 *   <li>⚠️ <b>首次跑可能因 H2 / MySQL 的 {@code information_schema} 差异而红</b> —— 红时必须先分清两类原因，
 *       否则会把「断言不兼容」误判成「DDL 漂了」而乱改表结构：<br>
 *       ① <b>断言不兼容</b>：H2 2.3.232 与 MySQL 在列名大小写（本用例 {@code DATABASE_TO_LOWER=TRUE} ⇒ 全小写）、
 *       {@code constraint_type} 的字面量、以及「内联 {@code UNIQUE} 是否出现在 {@code table_constraints} 里」
 *       上并不完全一致；② <b>schema 真漂了</b>：列 / 唯一键确实不在。<br>
 *       <b>判别方法</b>：失败信息里已经带**实有清单** —— 清单里列名只是大小写不同 ⇒ ①（改断言写法）；
 *       清单里真的没有它 ⇒ ②（同步 {@code Initialize.sql} 与本文件 DDL）。
 *       两类都**不许用「删断言」解决**（那正是把双源同步又变回「靠记得」）。</li>
 * </ul>
 *
 * <p><b>四条判据（第一步验收 + 两条对账等式）</b>：
 * <table border="1">
 *   <caption>判据口径</caption>
 *   <tr><th>判据</th><th>口径</th><th>不绿说明什么</th></tr>
 *   <tr><td>数值一致</td><td>每商家 {@code settled_total == Σ settlement_amount WHERE status = SETTLED}</td>
 *       <td>「加」与状态迁移没同事务</td></tr>
 *   <tr><td>覆盖面一致</td><td>账户行集合 == 「有 {@code SETTLED} 结算单的商家」集合</td>
 *       <td>少建/多建了账户行</td></tr>
 *   <tr><td><b>非空断言</b></td><td>已结算集合为空 ⇒ <b>本用例必须失败</b></td>
 *       <td>还没跑通一条真实结算 —— 空集上的自检只会安静地绿</td></tr>
 *   <tr><td><b>对账等式（已打款）</b></td>
 *       <td>每商家 {@code Σ withdrawal.amount WHERE status = PAID_OUT == paid_total}</td>
 *       <td>记账没走「审核 → 条件迁移 → 记账」这条唯一路径（或多记/漏记）</td></tr>
 * </table>
 * 最后一条的分母写死：<b>已打款的事实源是 {@code withdrawal}，{@code settlement} 不提供</b>
 * （{@code SETTLED} = 可提现，不代表「未打款」）⇒ 两条等式各自独立，不能互相推导。
 *
 * <p>非空断言之所以能成立，是因为本用例<b>自己造数据并真的走一遍 {@code settle()}</b> / {@code review()}：
 * 若数据靠「直接插一行 status=SETTLED」准备，判据能过而入口可能一行都不通。
 *
 * <p><b>对账为什么用直查 SQL、不走生产仓储</b>：要量的是「库里的账」，若借
 * {@code SellerFundAccountRepository} 读、再拿它比自己，就等于让被测对象给自己出题。
 * 故所有判据都用 JDBC 直查（同时也就避免了让 PO 出 persistence 层）。
 *
 * <p><b>取数口径 = 第一步的现状，刻意用最严的那一版</b>：只取
 * {@code settlement.status = SETTLED}（**不含 PAID_OUT**）。
 * <ul>
 *   <li>第一步没有任何路径把结算单迁出 SETTLED ⇒ 只取 SETTLED 就是「已结算」的准确表达；</li>
 *   <li>而且它**更严**：假如第一步里有行被错误地迁成 PAID_OUT，{@code settled_total} 仍含那笔钱，
 *       判据当场红 —— 若取 {@code IN (SETTLED, PAID_OUT)} 反而会把这类错误盖住。</li>
 *   <li>⚠️ <b>条件性提示（不是无条件待办）</b>：<b>只有当后续步骤仍决定把结算单行标成 PAID_OUT（历史语义）时</b>，
 *       才必须在同一个 commit 里把这两处取数改宽，否则判据会突然红：
 *       <ol>
 *         <li><b>数值一致</b> —— {@link #settledAmountBySeller()} 的 {@code WHERE status = ?} → {@code IN (SETTLED, PAID_OUT)}；</li>
 *         <li><b>覆盖面一致</b> —— {@link #settledSellersInLedger()} 的 {@code WHERE status = ?} → {@code IN (SETTLED, PAID_OUT)}。</li>
 *       </ol>
 *       但按本批裁定的状态语义（{@code SETTLED} = 「可提现」、已打款的事实源是 {@code withdrawal}、
 *       分配表与行状态解耦），**预期后续步骤不写 PAID_OUT** ⇒ 两处取数应保持 SETTLED-only、无需改动。</li>
 * </ul>
 *
 * <p><b>本用例不覆盖</b>（属第二步/后续）：{@code frozen_amount} 的预占与释放（第一步刻意不写它）、
 * 按金额**核销**（{@code withdrawal_settlement} 分配表 —— 它不负责把结算单行标成已打款）。
 * 也不验证事务真实性：直接 {@code new} 出来的 Manager 没有 Spring 事务代理，
 * 「注解在不在」由 {@code FundTransactionGuardTest} 静态守，回滚属集成测试范畴。
 */
@DisplayName("商家资金账户 H2 集成测试（settle 唯一入口 + 四条判据）")
class SettlementFundAccountH2Test {

    private static final long SELLER_SETTLE = 9001L;
    private static final long SELLER_IDEMPOTENT = 9002L;
    private static final long SELLER_GUARD = 9003L;
    private static final long SELLER_PAYOUT = 9004L;
    private static final long SELLER_SELF_CHECK = 9005L;
    private static final long SELLER_RECONCILE = 9006L;

    private static final int AMOUNT = 12_550;
    private static final int PAID_PART = 5_000;
    private static final int REJECTED_PART = 700;

    private static DataSource dataSource;
    private static SettlementRepository settlementRepository;
    private static SellerFundAccountRepository fundAccountRepository;
    private static WithdrawalRepository withdrawalRepository;
    private static WithdrawalSettlementRepository withdrawalSettlementRepository;
    private static SqlSession sharedSession;
    private static org.icedamericanomall.trade.app.finance.WithdrawalPayoutService payoutService;
    private static SettlementManager settlementManager;
    private static WithdrawalManager withdrawalManager;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:fundaccountdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE settlement (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    settlement_no BINARY(16) NOT NULL UNIQUE,
                    seller_id BIGINT NOT NULL,
                    period_start DATE NOT NULL,
                    period_end DATE NOT NULL,
                    order_count INT NOT NULL DEFAULT 0,
                    total_amount INT NOT NULL DEFAULT 0,
                    commission INT NOT NULL DEFAULT 0,
                    settlement_amount INT NOT NULL DEFAULT 0,
                    withdrawn_amount INT NOT NULL DEFAULT 0,
                    status TINYINT NOT NULL DEFAULT 1,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
            stmt.execute("""
                CREATE TABLE seller_fund_account (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    seller_id BIGINT NOT NULL,
                    settled_total INT NOT NULL DEFAULT 0,
                    frozen_amount INT NOT NULL DEFAULT 0,
                    paid_total INT NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_seller_id UNIQUE (seller_id)
                )""");
            stmt.execute("""
                CREATE TABLE withdrawal (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    withdrawal_no BINARY(16) NOT NULL UNIQUE,
                    seller_id BIGINT NOT NULL,
                    amount INT NOT NULL,
                    payout_no VARCHAR(64),
                    status TINYINT NOT NULL DEFAULT 1,
                    bank_account VARCHAR(50),
                    bank_name VARCHAR(100),
                    admin_remark VARCHAR(500),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");

            stmt.execute("""
                CREATE TABLE withdrawal_settlement (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    withdrawal_id BIGINT NOT NULL,
                    settlement_id BIGINT NOT NULL,
                    amount INT NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_withdrawal_settlement UNIQUE (withdrawal_id, settlement_id)
                )""");
        }

        // 手写 DDL 的「记得同步」不靠自觉：启动即对结构做断言（漏列 / 漏唯一键当场红）
        assertSchemaMatchesProduction();

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SettlementMapper.class);
        config.addMapper(SellerFundAccountMapper.class);
        config.addMapper(WithdrawalMapper.class);
        config.addMapper(WithdrawalSettlementMapper.class);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // 生产：一个事务一个 SqlSession（Spring 管），一级缓存（默认 SESSION）靠「同 session 内写会清缓存」保证不脏读。
        // 本用例原本每个仓储各持一个长活 session，那是假环境 —— 会让 A 仓储的写对 B 仓储不可见（核销候选曾因此偏大）。
        // 这里改为**共用一个 session**：与生产的 session 生命周期语义一致，同时保持默认 SESSION 级缓存不变。
        sharedSession = sqlSessionFactory.openSession(false);   // 关自动提交：本用例按「一业务方法一事务」提交/回滚
        settlementRepository = new SettlementRepositoryImpl(
                sharedSession.getMapper(SettlementMapper.class));
        fundAccountRepository = new SellerFundAccountRepositoryImpl(
                sharedSession.getMapper(SellerFundAccountMapper.class));
        withdrawalRepository = new WithdrawalRepositoryImpl(
                sharedSession.getMapper(WithdrawalMapper.class));
        withdrawalSettlementRepository = new WithdrawalSettlementRepositoryImpl(
                sharedSession.getMapper(WithdrawalSettlementMapper.class),
                sharedSession.getMapper(SettlementMapper.class));
        // 结算/打款只用到三个仓储；出参转换与订单查询不在本用例路径上
        settlementManager = new SettlementManager(settlementRepository, fundAccountRepository, null,
                new TradeConverterImpl());
        withdrawalManager = new WithdrawalManager(withdrawalRepository, fundAccountRepository,
                settlementRepository, withdrawalSettlementRepository, settlementManager,
                new TradeConverterImpl());
        payoutService = new org.icedamericanomall.trade.app.finance.WithdrawalPayoutService(
                withdrawalRepository, new org.icedamericanomall.trade.infrastructure.adapter.payout.ManualPayoutAdapter(),
                withdrawalManager);
        // 生产默认关闭打款（只记账不付款）；H2 用例要验打款/核销路径，显式打开
        org.springframework.test.util.ReflectionTestUtils.setField(withdrawalManager, "payoutEnabled", true);
    }

    // ─────────────────────── 结构断言（双源同步的可执行检查） ───────────────────────

    /** 与 {@code Initialize.sql} 对齐的关键列 / 唯一键；漏一个就红（替掉「靠记得同步」）。 */
    private static void assertSchemaMatchesProduction() throws SQLException {
        assertColumns("settlement", "id", "settlement_no", "seller_id", "period_start", "period_end",
                "settlement_amount", "withdrawn_amount",
                "order_count", "total_amount", "commission", "settlement_amount", "status",
                "create_time", "update_time");
        assertColumns("seller_fund_account", "id", "seller_id", "settled_total", "frozen_amount",
                "paid_total", "create_time", "update_time");
        assertColumns("withdrawal", "id", "withdrawal_no", "seller_id", "amount", "status",
                "bank_account", "bank_name", "admin_remark", "create_time", "update_time");

        assertUniqueOn("settlement", List.of("settlement_no"));
        assertColumns("withdrawal_settlement", "id", "withdrawal_id", "settlement_id", "amount", "create_time");
        assertUniqueOn("withdrawal_settlement", List.of("withdrawal_id", "settlement_id"));
        assertUniqueOn("seller_fund_account", List.of("seller_id"));
        assertUniqueOn("withdrawal", List.of("withdrawal_no"));
    }

    private static void assertColumns(String table, String... expected) throws SQLException {
        List<String> actual = columnNamesOf(table);
        assertFalse(actual.isEmpty(), "information_schema 里查不到表 " + table + " —— 表都没建出来");
        for (String column : expected) {
            assertTrue(actual.contains(column),
                    "H2 DDL 与 Initialize.sql 脱节：表 " + table + " 缺少列 `" + column + "`（实有：" + actual + "）");
        }
    }

    /** 断言存在「列集恰好等于 expected」的唯一键（不认键名：生产里有内联 UNIQUE，名字是自动生成的）。 */
    private static void assertUniqueOn(String table, List<String> expected) throws SQLException {
        List<List<String>> keys = uniqueColumnSetsOf(table);
        assertTrue(keys.contains(expected),
                "表 " + table + " 缺唯一键 " + expected + "（实有：" + keys + "）—— 幂等/去重全靠它，"
                        + "H2 少了它会让用例在更宽松的约束下通过");
    }

    private static List<String> columnNamesOf(String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private static List<List<String>> uniqueColumnSetsOf(String table) throws SQLException {
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        String sql = "SELECT c.constraint_name AS name, k.column_name AS col "
                + "FROM information_schema.table_constraints c "
                + "JOIN information_schema.key_column_usage k "
                + "ON k.constraint_name = c.constraint_name AND k.table_name = c.table_name "
                + "WHERE c.table_name = ? AND c.constraint_type = 'UNIQUE' "
                + "ORDER BY c.constraint_name, k.ordinal_position";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byKey.computeIfAbsent(rs.getString("name"), name -> new ArrayList<>())
                            .add(rs.getString("col"));
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    // ─────────────────────── 造数据 ───────────────────────

    /** 造一张待结算单：账期结束日、结算金额、订单数都可控（用来看住四个可结算条件）。 */
    private static Long savePending(long sellerId, int settlementAmount, LocalDate periodEnd, int orderCount) {
        Settlement settlement = new Settlement();
        settlement.setSettlementNo(IdGenerator.nextId());
        settlement.setSellerId(sellerId);
        settlement.setPeriodStart(periodEnd.minusDays(7));
        settlement.setPeriodEnd(periodEnd);
        settlement.setOrderCount(orderCount);
        settlement.setTotalAmount(settlementAmount);
        settlement.setCommission(0);
        settlement.setSettlementAmount(settlementAmount);
        settlement.setStatus(SettlementStatusEnum.PENDING.getCode());
        Settlement saved = inTx(() -> settlementRepository.save(settlement));
        assertNotNull(saved.getId(), "造数据失败：结算单没拿到主键");
        return saved.getId();
    }

    /** 造一张待审核提现单（金额可控；状态迁移由 {@code WithdrawalManager#review} 真实执行）。 */

    private static LocalDate yesterday() {
        return LocalDate.now().minusDays(1);
    }

    private static int settledTotalOf(long sellerId) {
        SellerFundAccount account = fundAccountRepository.findBySellerId(sellerId);
        assertNotNull(account, "商家 " + sellerId + " 还没有资金账户行（settle() 应建行）");
        return account.getSettledTotal();
    }

    // ─────────────────────── 对账（直查 SQL） ───────────────────────

    /**
     * 判据①：每商家的「已结算金额合计」。
     *
     * <p>只取 {@code SETTLED}（= 已结算 / 可提现，见类注释的状态语义）。只有「把结算单行标成 PAID_OUT」
     * 的批次才需要改宽成 {@code IN (SETTLED, PAID_OUT)}；按本批裁定预期不需要。
     */
    private static Map<Long, Integer> settledAmountBySeller() throws SQLException {
        Map<Long, Integer> bySeller = new HashMap<>();
        String sql = "SELECT seller_id, SUM(settlement_amount) FROM settlement"
                + " WHERE status = ? GROUP BY seller_id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SettlementStatusEnum.SETTLED.getCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bySeller.put(rs.getLong(1), rs.getInt(2));
                }
            }
        }
        return bySeller;
    }

    /** 判据②：库里的资金账户行（商家集合）。 */
    private static Set<Long> accountSellerIds() throws SQLException {
        return sellerIdsOf("SELECT seller_id FROM seller_fund_account");
    }

    /** 判据②的对照面：有已结算单的商家集合（同样只取 {@code SETTLED}，改宽条件同判据①）。 */
    private static Set<Long> settledSellersInLedger() throws SQLException {
        Set<Long> sellers = new HashSet<>();
        String sql = "SELECT DISTINCT seller_id FROM settlement WHERE status = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SettlementStatusEnum.SETTLED.getCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sellers.add(rs.getLong(1));
                }
            }
        }
        return sellers;
    }

    private static Set<Long> sellerIdsOf(String sql) throws SQLException {
        Set<Long> ids = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /** 单值整数查询（对账用；无行返回 0）。 */
    /** 直连 JDBC 改一行（造漂移用；与 session 无关，所以前面必须已提交）。 */
    private static int exec(String sql, Object... args) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static int intOf(String sql, Object... args) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ─────────────────────── ① 真实走通 settle() ───────────────────────

    @Test
    @DisplayName("★ settle — 造数据后真的走通唯一入口：状态变 SETTLED 且 settled_total 同事务增加")
    void shouldSetSettledAndIncreaseSettledTotal_whenPeriodEndedAndAmountPositive() {
        Long id = savePending(SELLER_SETTLE, AMOUNT, yesterday(), 3);

        assertTrue(settleInTx(id), "账期已走完、金额>0、有订单的待结算单必须被结算");

        Settlement reloaded = settlementRepository.findById(id);
        assertEquals(SettlementStatusEnum.SETTLED.getCode(), reloaded.getStatus().intValue(),
                "状态必须真的从 PENDING 迁到 SETTLED");
        assertEquals(AMOUNT, settledTotalOf(SELLER_SETTLE), "settled_total 必须与状态迁移同事务增加");
        assertEquals(0, fundAccountRepository.findBySellerId(SELLER_SETTLE).getFrozenAmount().intValue(),
                "第一步不写 frozen_amount：它的「加」在第二步 applyWithdrawal 预占时才出现");
    }

    @Test
    @DisplayName("settle — 幂等：同一单再结算返回 false，settled_total 不再增长")
    void shouldBeNoOp_whenSettleCalledTwice() {
        Long id = savePending(SELLER_IDEMPOTENT, AMOUNT, yesterday(), 2);

        assertTrue(settleInTx(id));
        assertEquals(AMOUNT, settledTotalOf(SELLER_IDEMPOTENT));

        assertFalse(settleInTx(id), "已结算再调 = 0 行，幂等 no-op");
        assertEquals(AMOUNT, settledTotalOf(SELLER_IDEMPOTENT), "重复调用不得重复累加");
        assertEquals(SettlementStatusEnum.SETTLED.getCode(),
                settlementRepository.findById(id).getStatus().intValue());
    }

    @Test
    @DisplayName("settle — 四个可结算条件：未到期 / 零额 / 无订单 / 不存在 一律 0 行且账不落")
    void shouldNotSettle_whenConditionsNotMet() {
        Long notDue = savePending(SELLER_GUARD, AMOUNT, LocalDate.now().plusDays(3), 2);
        Long zeroAmount = savePending(SELLER_GUARD, 0, yesterday(), 2);
        Long noOrders = savePending(SELLER_GUARD, AMOUNT, yesterday(), 0);

        assertFalse(settleInTx(notDue), "账期未走完不得结算");
        assertFalse(settleInTx(zeroAmount), "零额单不结算（否则白占余额）");
        assertFalse(settleInTx(noOrders), "无订单的账期不结算");
        assertFalse(settleInTx(999_999_999L), "不存在的结算单 = no-op，不抛异常");

        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(notDue).getStatus().intValue());
        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(zeroAmount).getStatus().intValue());
        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(noOrders).getStatus().intValue());
        assertNull(fundAccountRepository.findBySellerId(SELLER_GUARD),
                "一张都没结算成功过 ⇒ 不得建出空账户行（否则覆盖面判据会出现假的多行）");
    }

    // ─────────────────────── ② 打款只动 paid_total ───────────────────────

    @Test
    @DisplayName("payout — 只累加 paid_total（settled_total 不动）；**不做额度判断**（额度属申请侧）")
    void shouldIncreasePaidTotalOnly_whenPayout() {
        Long id = savePending(SELLER_PAYOUT, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(id));
        int available = settlementManager.availableBalance(SELLER_PAYOUT);
        assertEquals(AMOUNT, available, "可提现额 = 已结算 − 冻结 − 已打款");

        settlementManager.payout(SELLER_PAYOUT, PAID_PART);

        SellerFundAccount account = fundAccountRepository.findBySellerId(SELLER_PAYOUT);
        assertEquals(PAID_PART, account.getPaidTotal().intValue());
        assertEquals(AMOUNT, account.getSettledTotal().intValue(), "打款不动 settled_total（否则同一笔钱被扣两次）");
        assertEquals(available - PAID_PART, settlementManager.availableBalance(SELLER_PAYOUT));

        // 审核侧**不判额度**（额度属申请侧 applyWithdrawal；第二步做成条件预占）。
        // 下面两条是**断言**（不是观察），锁的正是「review 不判额度」这条裁决：
        // 若有人把额度条件加回审核侧，`increasePaidTotal` 会命中 0 行、记账被拒 ⇒ paid_total 停在前一值，
        // 第一条断言立刻红；第二条同时红（可用额不会掉到负数）。
        settlementManager.payout(SELLER_PAYOUT, settlementManager.availableBalance(SELLER_PAYOUT) + 1);
        assertEquals(AMOUNT + 1, fundAccountRepository.findBySellerId(SELLER_PAYOUT).getPaidTotal().intValue(),
                "本条锁「review 不判额度」：审核侧记账不得被「可用额」拦下（守门在申请侧）；"
                        + "若把额度条件加回 review，paid_total 会停在前一值");
        assertEquals(-1, settlementManager.availableBalance(SELLER_PAYOUT),
                "本条同锁「review 不判额度」：超额记账后可用额为负 —— 这是 fail-closed 信号"
                        + "（后续申请会被申请侧拒掉），不是在这里被拦住；被拦住则该值为 0 或正数");

        BizException ex = assertThrows(BizException.class, () -> settlementManager.payout(SELLER_PAYOUT, 0));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode(), "打款金额必须 > 0（数据不合法 ≠ 额度不够）");
    }

    // ─────────────────────── ③ 第一步验收：自检三条 ───────────────────────

    @Test
    @DisplayName("★ 自检三条（数值一致 / 覆盖面一致 / 非空断言）—— 空集必须失败，故本用例自己造数据走 settle()")
    void shouldPassFirstStepSelfChecks_withRealSettledData() throws SQLException {
        // 自检的数据必须来自真实 settle()，不能直接插一行 SETTLED（那能骗过自检，却证明不了入口是通的）
        Long id = savePending(SELLER_SELF_CHECK, AMOUNT, yesterday(), 4);
        assertTrue(settleInTx(id), "自检前置：settle() 必须真的把单子结算掉");

        Map<Long, Integer> settledBySeller = settledAmountBySeller();

        // 【非空断言】—— 落在「观测集合」上，不是落在「违规集合」上：0 行时自检必须红，
        // 否则「0 == 0」会让第一步在完全没跑通的情况下安静通过。
        assertFalse(settledBySeller.isEmpty(),
                "已结算集合为 0 行 ⇒ 自检必须失败：第一步还没跑通一条真实结算（空集上的自检只会安静地绿）");

        Set<Long> settledSellers = settledSellersInLedger();
        Set<Long> accountSellers = accountSellerIds();
        assertFalse(accountSellers.isEmpty(), "已结算非空却没有任何资金账户行 ⇒ 覆盖面自检必然红");

        // 【数值一致】settled_total == 该商家已结算金额合计
        for (Long sellerId : accountSellers) {
            SellerFundAccount account = fundAccountRepository.findBySellerId(sellerId);
            int expected = settledBySeller.getOrDefault(sellerId, 0);
            assertEquals(expected, account.getSettledTotal().intValue(),
                    "数值一致失败：商家 " + sellerId
                            + " 的 settled_total 与「Σ 已结算金额」不一致（「加」与状态迁移没同事务？）");
        }

        // 【覆盖面一致】账户行集合 == 有已结算单的商家集合（少建行/多建空行都红）
        assertEquals(settledSellers, accountSellers,
                "覆盖面一致失败：有已结算单的商家与资金账户行不是一一对应");
    }

    // ─────────────────────── ④ 对账等式：已打款 ───────────────────────

    @Test
    @DisplayName("★ 对账等式 — Σ withdrawal(PAID_OUT) == paid_total（每商家）；settlement 不提供打款事实")
    void shouldReconcilePaidTotalAgainstPaidOutWithdrawals() throws SQLException {
        // 造数据：真实结算 + 真实审核（一个打款、一个拒绝）
        Long settlementId = savePending(SELLER_RECONCILE, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));

        Long paidWithdrawalId = withdraw(SELLER_RECONCILE, PAID_PART);
        payoutInTx(paidWithdrawalId);

        Long rejectedWithdrawalId = withdraw(SELLER_RECONCILE, REJECTED_PART);
        reviewInTx(rejectedWithdrawalId, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        int paidTotal = intOf("SELECT paid_total FROM seller_fund_account WHERE seller_id = ?", SELLER_RECONCILE);
        assertEquals(0, frozenOf(SELLER_RECONCILE), "两笔都到终态后预占必须归零");
        int paidOutSum = intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal WHERE seller_id = ? AND status = ?",
                SELLER_RECONCILE, WithdrawalStatusEnum.PAID_OUT.getCode());
        int rejectedSum = intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal WHERE seller_id = ? AND status = ?",
                SELLER_RECONCILE, WithdrawalStatusEnum.REJECTED.getCode());

        assertEquals(PAID_PART, paidTotal, "只有「审核为已打款」的那笔进 paid_total");
        assertEquals(REJECTED_PART, rejectedSum, "被拒的单确实存在（不是没造出来）");
        assertEquals(paidOutSum, paidTotal,
                "★ 对账等式：Σ withdrawal.amount WHERE status = PAID_OUT 必须等于 paid_total —— "
                        + "已打款的事实源是 withdrawal；settlement 不提供该等式（SETTLED 只表示可提现）");

        // settlement 侧是**另一条**等式，与上面互不推导
        int settledSum = intOf("SELECT COALESCE(SUM(settlement_amount), 0) FROM settlement "
                + "WHERE seller_id = ? AND status = ?", SELLER_RECONCILE, SettlementStatusEnum.SETTLED.getCode());
        assertEquals(AMOUNT, settledSum);
        assertEquals(settledSum, settledTotalOf(SELLER_RECONCILE),
                "★ 另一条等式：Σ settlement.settlement_amount WHERE status = SETTLED == settled_total");
    }

    @Test
    @DisplayName("触发入口 — 一次扫描把「到期」结算单结算掉、不动未到期的；重复扫描不再动账")
    void shouldSweepSettleableSettlementsOnly() {
        long sellerDueA = 9010L;
        long sellerDueB = 9011L;
        long sellerNotDue = 9012L;
        Long dueA = savePending(sellerDueA, AMOUNT, yesterday(), 1);
        Long dueB = savePending(sellerDueB, AMOUNT, yesterday(), 2);
        Long notDue = savePending(sellerNotDue, AMOUNT, LocalDate.now().plusDays(2), 1);

        SettlementSweeper sweeper = new SettlementSweeper(settlementRepository, settlementManager);
        LocalDateTime sweepNow = LocalDateTime.now();
        SettlementSweeper.SweepResult first = sweepInTx(sweeper, sweepNow.toLocalDate(), sweepNow, 200);
        assertEquals(0, first.failed(), "首次扫描不该有异常失败");

        assertEquals(SettlementStatusEnum.SETTLED.getCode(),
                settlementRepository.findById(dueA).getStatus().intValue(), "到期的必须被结算（= 可提现）");
        assertEquals(SettlementStatusEnum.SETTLED.getCode(),
                settlementRepository.findById(dueB).getStatus().intValue());
        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(notDue).getStatus().intValue(), "未到期的一条都不许动");
        assertEquals(AMOUNT, settledTotalOf(sellerDueA), "扫描结算同样要记账（与逐条 settle 同一入口）");
        assertEquals(AMOUNT, settledTotalOf(sellerDueB));

        // 断言「不再动账」，而不是断言 sweep 返回 0：本类共用一个库，其它用例可能留下可结算的行
        int settledBeforeResweep = settledTotalOf(sellerDueA) + settledTotalOf(sellerDueB);
        LocalDateTime resweepNow = LocalDateTime.now();
        sweepInTx(sweeper, resweepNow.toLocalDate(), resweepNow, 200);
        assertEquals(settledBeforeResweep, settledTotalOf(sellerDueA) + settledTotalOf(sellerDueB),
                "重扫不得重复累加（条件更新 0 行 = 已结算，跳过）");
        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(notDue).getStatus().intValue());
    }

    @Test
    @DisplayName("触发入口 — 按账期补跑：只结算指定 period_end 的单，其它照旧待结算")
    void shouldSweepOnlyGivenPeriodEnd() {
        long seller = 9040L;
        LocalDate targetPeriod = LocalDate.now().minusDays(10);
        Long target = savePending(seller, AMOUNT, targetPeriod, 1);
        Long other = savePending(seller, AMOUNT, LocalDate.now().minusDays(5), 1);

        SettlementSweeper sweeper = new SettlementSweeper(settlementRepository, settlementManager);
        LocalDateTime now = LocalDateTime.now();
        sweepInTx(sweeper, now.toLocalDate(), now, 200, targetPeriod);

        assertEquals(SettlementStatusEnum.SETTLED.getCode(),
                settlementRepository.findById(target).getStatus().intValue(), "指定账期必须被结算");
        assertEquals(SettlementStatusEnum.PENDING.getCode(),
                settlementRepository.findById(other).getStatus().intValue(), "其它账期不许被顺手结算");
        assertEquals(AMOUNT, settledTotalOf(seller));
    }

    // ─────────────────── 第二步：条件预占（frozen_amount）───────────────────

    @Test
    @DisplayName("★ 第二步 — 申请即预占：frozen 增加、可用额减少，并落「待审核」单")
    void shouldFreezeOnApply() {
        long seller = 9020L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        assertEquals(AMOUNT, settlementManager.availableBalance(seller));

        withdraw(seller, 3_000);

        assertEquals(3_000, frozenOf(seller), "申请成功必须先冻结，否则这笔钱能被再提一次");
        assertEquals(AMOUNT - 3_000, settlementManager.availableBalance(seller),
                "可用额 = settled_total − frozen_amount − paid_total");
        assertEquals(1, countWithdrawals(seller));
    }

    @Test
    @DisplayName("★ 第二步 — 额度不足：条件预占 0 行 ⇒ 拒单，且不落库、不占额度")
    void shouldRejectWhenQuotaInsufficient() {
        long seller = 9021L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        withdraw(seller, 3_000);

        BizException ex = assertThrows(BizException.class, () -> withdraw(seller, AMOUNT - 3_000 + 1));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode());

        assertEquals(3_000, frozenOf(seller), "失败的申请不得占用额度");
        assertEquals(1, countWithdrawals(seller), "失败的申请不得落库");
    }

    @Test
    @DisplayName("第二步 — 超过单笔上限：PARAM_ERROR，不占额度也不落库（上限与余额无关，独立判）")
    void shouldRejectWhenOverSingleLimit() {
        long seller = 9022L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));

        BizException ex = assertThrows(BizException.class, () -> withdraw(seller, 5_000_001));
        assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        assertEquals(0, frozenOf(seller));
        assertEquals(0, countWithdrawals(seller));
    }

    @Test
    @DisplayName("★ 第二步 — 审核打款：释放预占 + 记 paid_total（frozen 归零）")
    void shouldReleaseAndRecordPaidOnPaidOut() {
        long seller = 9023L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);

        payoutInTx(withdrawalId);

        assertEquals(0, frozenOf(seller), "打款后预占必须释放为 0");
        assertEquals(4_000, paidTotalOf(seller), "只把这一笔记进 paid_total");
        assertEquals(AMOUNT - 4_000, settlementManager.availableBalance(seller));
    }

    @Test
    @DisplayName("第二步 — 审核拒绝：释放预占、不动 paid_total，额度可再用")
    void shouldReleaseOnRejectAndKeepQuota() {
        long seller = 9024L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);

        reviewInTx(withdrawalId, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        assertEquals(0, frozenOf(seller), "拒绝也要释放预占（否则额度被永久占住）");
        assertEquals(0, paidTotalOf(seller), "拒绝不记账");
        assertEquals(AMOUNT, settlementManager.availableBalance(seller), "额度应完全恢复");
        withdraw(seller, 4_000);
        assertEquals(4_000, frozenOf(seller), "释放后可以再申请");
    }

    @Test
    @DisplayName("★ 第二步 — 重复审核：幂等，冻结额与 paid_total 都不再变（先迁移后动账的守门用例）")
    void shouldBeIdempotentOnReplayedReview() {
        long seller = 9025L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);

        payoutInTx(withdrawalId);
        reviewInTx(withdrawalId, WithdrawalStatusEnum.PAID_OUT.getCode(), "再点一次");

        assertEquals(0, frozenOf(seller));
        assertEquals(4_000, paidTotalOf(seller),
                "重复审核不得重复记账；若顺序写反（先扣 frozen 再迁移），这里会看到 frozen 被多扣一次");
    }

    @Test
    @DisplayName("★ 第二步对账等式：frozen_amount == Σ withdrawal.amount WHERE status = PENDING")
    void shouldReconcileFrozenAgainstPendingWithdrawals() throws SQLException {
        long seller = 9026L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long firstId = withdraw(seller, 3_000);
        withdraw(seller, 2_000);
        assertEquals(5_000, frozenOf(seller));

        // 审掉第一笔：预占 5000 → 剩 2000，paid 3000
        payoutInTx(firstId);

        // E3（A1 扩展）：占用冻结的不只是「待审核」，还有「付款中」——结果未知的钱必须继续占着
        int occupying = intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal "
                + "WHERE seller_id = ? AND status IN (?, ?)", seller,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.PAYING.getCode());
        assertEquals(occupying, frozenOf(seller),
                "★ 第三条等式：frozen_amount 必须等于「待审核 + 付款中」金额之和 —— "
                        + "漏掉付款中就等于「结果未知的钱先把额度还了」，可能被再提一次");
        assertEquals(2_000, occupying);
        assertEquals(3_000, paidTotalOf(seller));
    }

    @Test
    @DisplayName("A1 — 受理后停在「付款中」：仍占预占、不记 paid_total、不进 E2（结果未知≠已付款）")
    void shouldKeepFrozenHeld_whenWithdrawalIsPaying() throws SQLException {
        long seller = 9051L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);

        reviewInTx(withdrawalId, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");

        assertEquals(WithdrawalStatusEnum.PAYING.getCode(),
                withdrawalRepository.findById(withdrawalId).getStatus().intValue(),
                "受理后必须停在付款中，不能直接跳到已打款");
        assertEquals(4_000, frozenOf(seller), "★ 付款中仍占冻结：否则这笔钱能被再提一次");
        assertEquals(0, paidTotalOf(seller), "★ 未拿到回执不得记 paid_total（否则 E2 变成假的已付款）");
        int occupying = intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal "
                + "WHERE seller_id = ? AND status IN (?, ?)", seller,
                WithdrawalStatusEnum.PENDING_REVIEW.getCode(), WithdrawalStatusEnum.PAYING.getCode());
        assertEquals(occupying, frozenOf(seller), "E3 必须把付款中算进占用");
    }

    // ─────────────────── 第三步：按金额核销（分配表）───────────────────

    @Test
    @DisplayName("★ 第三步 — 一笔提现跨两张结算单：按账期 FIFO 核销（先吃满旧的）")
    void shouldAllocateAcrossSettlementsInFifoOrder() throws SQLException {
        long seller = 9030L;
        Long older = savePending(seller, 10_000, LocalDate.now().minusDays(30), 1);
        Long newer = savePending(seller, 20_000, yesterday(), 1);
        assertTrue(settleInTx(older));
        assertTrue(settleInTx(newer));

        Long withdrawalId = withdraw(seller, 15_000);

        assertEquals(10_000, allocationOf(withdrawalId, older), "旧单先被吃满（FIFO）");
        assertEquals(5_000, allocationOf(withdrawalId, newer), "剩余的才落到新单");
        assertEquals(10_000, withdrawnOf(older));
        assertEquals(5_000, withdrawnOf(newer));
        assertEquals(15_000, frozenOf(seller), "账户冻结与单据核销是同一件事的两个投影");
    }

    @Test
    @DisplayName("★ 第三步 — 拒绝：删分配行 + 回退 withdrawn_amount + 解冻（两个维度一起退）")
    void shouldReleaseAllocationOnReject() throws SQLException {
        long seller = 9031L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);
        assertEquals(4_000, withdrawnOf(settlementId));

        reviewInTx(withdrawalId, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");

        assertEquals(0, allocationCount(withdrawalId), "拒绝必须删分配行（不留 released 标记）");
        assertEquals(0, withdrawnOf(settlementId), "withdrawn_amount 必须回退");
        assertEquals(0, frozenOf(seller), "冻结额必须释放");
        assertEquals(AMOUNT, settlementManager.availableBalance(seller), "额度完全恢复");
    }

    @Test
    @DisplayName("第三步 — 打款：保留分配行（钱确实从这些单出），withdrawn_amount 不再变")
    void shouldKeepAllocationOnPayout() throws SQLException {
        long seller = 9032L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));
        Long withdrawalId = withdraw(seller, 4_000);

        payoutInTx(withdrawalId);

        assertEquals(1, allocationCount(withdrawalId), "打款保留分配行 —— 它是 E6 闭环的依据");
        assertEquals(4_000, withdrawnOf(settlementId), "withdrawn_amount 不再变");
        assertEquals(0, frozenOf(seller));
        assertEquals(4_000, paidTotalOf(seller));
    }

    @Test
    @DisplayName("★ 第三步 — 一张单被多笔提现部分占用：withdrawn_amount 累计到结算额为止")
    void shouldAccumulatePartialAllocationsOnOneSettlement() throws SQLException {
        long seller = 9033L;
        Long settlementId = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(settlementId));

        withdraw(seller, 3_000);
        withdraw(seller, 2_000);
        assertEquals(5_000, withdrawnOf(settlementId), "两张提现单各核销一部分");
        assertEquals(AMOUNT - 5_000, settlementManager.availableBalance(seller));

        withdraw(seller, AMOUNT - 5_000);
        assertEquals(AMOUNT, withdrawnOf(settlementId), "吃满到结算额，不多不少");

        BizException ex = assertThrows(BizException.class, () -> withdraw(seller, 1));
        assertEquals(ErrorCode.BALANCE_INSUFFICIENT, ex.getErrorCode(), "核销吃满后额度为 0，再提必须被拒");
    }

    @Test
    @DisplayName("★ 第三步的三条等式：E4 列↔明细、E5 提现↔明细、E6 跨表闭环")
    void shouldReconcileAllocationEquations() throws SQLException {
        long seller = 9034L;
        Long a = savePending(seller, 10_000, LocalDate.now().minusDays(40), 1);
        Long b = savePending(seller, AMOUNT, yesterday(), 1);
        assertTrue(settleInTx(a));
        assertTrue(settleInTx(b));

        Long paid = withdraw(seller, 12_000);          // 跨单：a 吃满 10000 + b 2000
        payoutInTx(paid);
        Long pending = withdraw(seller, 500);           // 还剩 b 的 10550
        Long rejected = withdraw(seller, 300);
        reviewInTx(rejected, WithdrawalStatusEnum.REJECTED.getCode(), "信息不全");
        Long pendingAfterReject = withdraw(seller, 500);

        // E4：逐单 withdrawn_amount == Σ 明细；且不超过结算额；非 SETTLED 必须为 0
        for (Long settlementId : List.of(a, b)) {
            assertEquals(sumAllocationsOfSettlement(settlementId), withdrawnOf(settlementId),
                    "★ E4：settlement.withdrawn_amount 必须等于该单的明细合计");
            assertTrue(withdrawnOf(settlementId) <= settlementAmountOf(settlementId),
                    "E4：withdrawn_amount 不得超过 settlement_amount");
        }
        assertEquals(0, withdrawnOfAnyPendingSettlement(),
                "E4：非 SETTLED 单的 withdrawn_amount 必须为 0（当前恒真：只有 SETTLED 单会被核销）");

        // E5：每笔的明细合计 == 提现金额；被拒 == 0
        assertEquals(12_000, sumAllocationsOfWithdrawal(paid));
        assertEquals(500, sumAllocationsOfWithdrawal(pending));
        assertEquals(0, sumAllocationsOfWithdrawal(rejected), "★ E5：被拒的提现不得留任何明细");
        assertEquals(500, sumAllocationsOfWithdrawal(pendingAfterReject));

        // E6：Σ(已打款提现的明细) == paid_total
        // 按商家收口：本类共用一个库，其它用例的已打款提现不能算进这条等式
        int paidAllocations = intOf("SELECT COALESCE(SUM(ws.amount), 0) FROM withdrawal_settlement ws "
                + "JOIN withdrawal w ON w.id = ws.withdrawal_id WHERE w.status = ? AND w.seller_id = ?",
                WithdrawalStatusEnum.PAID_OUT.getCode(), seller);
        assertEquals(paidTotalOf(seller), paidAllocations,
                "★ E6：明细合计（已打款）必须等于 paid_total —— 把两张表钉在一起");
    }

    // ─────────────────── 第三步用的小工具 ───────────────────

    private static int withdrawnOf(long settlementId) throws SQLException {
        return intOf("SELECT withdrawn_amount FROM settlement WHERE id = ?", settlementId);
    }

    private static int settlementAmountOf(long settlementId) throws SQLException {
        return intOf("SELECT settlement_amount FROM settlement WHERE id = ?", settlementId);
    }

    private static int allocationOf(long withdrawalId, long settlementId) throws SQLException {
        return intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal_settlement "
                + "WHERE withdrawal_id = ? AND settlement_id = ?", withdrawalId, settlementId);
    }

    private static int allocationCount(long withdrawalId) throws SQLException {
        return intOf("SELECT COUNT(*) FROM withdrawal_settlement WHERE withdrawal_id = ?", withdrawalId);
    }

    private static int sumAllocationsOfWithdrawal(long withdrawalId) throws SQLException {
        return intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal_settlement WHERE withdrawal_id = ?",
                withdrawalId);
    }

    private static int sumAllocationsOfSettlement(long settlementId) throws SQLException {
        return intOf("SELECT COALESCE(SUM(amount), 0) FROM withdrawal_settlement WHERE settlement_id = ?",
                settlementId);
    }

    /** 非 SETTLED 结算单上被核销的金额合计（当前应当恒为 0）。 */
    private static int withdrawnOfAnyPendingSettlement() throws SQLException {
        return intOf("SELECT COALESCE(SUM(withdrawn_amount), 0) FROM settlement WHERE status <> ?",
                SettlementStatusEnum.SETTLED.getCode());
    }


    // ─────────────────── 事务边界（harness 必须与生产同语义） ───────────────────

    /**
     * 在一个事务里跑一段写，成功提交、异常回滚。
     *
     * <p><b>为什么必须有</b>：生产里 manager 的 {@code @Transactional} 是 Spring 代理给的；
     * 本用例手搓仓储、没有 Spring，若不自己开事务，#{@code openSession()} 默认自动提交，
     * 于是「冻结成功、核销抛异常」会留下**半截脏账**，而生产会整体回滚 ——
     * 那正好是「测试绿、生产错」的经典形态。
     *
     * <p>⚠ <b>本 harness 只能验「提交」，验不了「回滚」</b>：这里没有 Spring 事务管理器，
     * 实测 {@code SqlSession.rollback()} 不会真的回滚（状态迁移会留在库里）。
     * 所以「审核中途失败整笔回滚」目前是<b>未覆盖项</b>（open-items §1.32）——
     * 要真验需 {@code @SpringBootTest} + {@code DataSourceTransactionManager}（或 Testcontainers）。
     */
    private static boolean settleInTx(Long settlementId) {
        return inTx(() -> settlementManager.settle(settlementId));
    }

    /**
     * 走完整两阶段：受理（待审核 → 付款中）→ 出款执行器（拿回执 → 已打款 + 释放预占 + 记 paid_total）。
     *
     * <p>与生产同形：受理在事务里，出款在事务外调渠道，落账回到事务里。
     */
    private static void payoutInTx(Long id) {
        reviewInTx(id, WithdrawalStatusEnum.PAID_OUT.getCode(), "已打款");
        inTx(() -> payoutService.settle(100));
    }

    private static void reviewInTx(Long id, Integer status, String remark) {
        inTx(() -> {
            withdrawalManager.review(id, status, remark);
            return null;
        });
    }

    private static SettlementSweeper.SweepResult sweepInTx(SettlementSweeper sweeper, LocalDate today,
                                                           LocalDateTime now, int limit) {
        return inTx(() -> sweeper.sweep(today, now, limit));
    }

    private static SettlementSweeper.SweepResult sweepInTx(SettlementSweeper sweeper, LocalDate today,
                                                           LocalDateTime now, int limit, LocalDate periodEnd) {
        return inTx(() -> sweeper.sweep(today, now, limit, periodEnd));
    }

    private static <T> T inTx(java.util.function.Supplier<T> op) {
        try {
            T result = op.get();
            session().commit();
            return result;
        } catch (RuntimeException e) {
            session().rollback();
            throw e;
        }
    }

    private static void inTx(Runnable op) {
        inTx(() -> {
            op.run();
            return null;
        });
    }

    private static SqlSession session() {
        return sharedSession;
    }

    // ─────────────────── 第二步用的小工具 ───────────────────

    private static Long withdraw(long sellerId, int amount) {
        WithdrawalApplyReq req = new WithdrawalApplyReq();
        req.setAmount(amount);
        req.setBankAccount("6222****1234");
        req.setBankName("招商银行");
        return inTx(() -> withdrawalManager.applyWithdrawal(sellerId, req).getId());
    }

    private static int frozenOf(long sellerId) {
        try {
            return intOf("SELECT frozen_amount FROM seller_fund_account WHERE seller_id = ?", sellerId);
        } catch (SQLException e) {
            throw new IllegalStateException("查询 frozen_amount 失败", e);
        }
    }

    private static int paidTotalOf(long sellerId) {
        try {
            return intOf("SELECT paid_total FROM seller_fund_account WHERE seller_id = ?", sellerId);
        } catch (SQLException e) {
            throw new IllegalStateException("查询 paid_total 失败", e);
        }
    }

    private static int countWithdrawals(long sellerId) {
        try {
            return intOf("SELECT COUNT(*) FROM withdrawal WHERE seller_id = ?", sellerId);
        } catch (SQLException e) {
            throw new IllegalStateException("查询 withdrawal 条数失败", e);
        }
    }

}
