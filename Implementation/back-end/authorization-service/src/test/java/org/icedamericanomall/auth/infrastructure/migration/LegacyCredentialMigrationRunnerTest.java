package org.icedamericanomall.auth.infrastructure.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.exception.DuplicateIdentityException;
import org.icedamericanomall.auth.infrastructure.persistence.userauth.UserAuthMapper;
import org.icedamericanomall.auth.infrastructure.persistence.userauth.UserAuthRepositoryImpl;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 存量凭证迁移器 H2 集成测试（V5.2）—— 三个迁移步骤的正确性与幂等性。
 *
 * <p>真实 JdbcTemplate（user 表旧列）+ 真实 UserAuthRepositoryImpl（user_auth 表）；
 * 验证：密码搬迁并清理旧列、手机号三字段加密并清理明文、微信身份建档、重复执行幂等、
 * 单行失败不中断整批。
 */
@DisplayName("LegacyCredentialMigrationRunner H2 集成测试")
class LegacyCredentialMigrationRunnerTest {

    private static final String LEGACY_PASSWORD_HASH = "$2a$10$legacyPasswordHashXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";

    /** 存量行的 {@code user_id} 业务键（32-hex；列是 {@code BINARY(16)}，落库前换原始字节）。 */
    private static final String USER_BIZ_1 = "0192f0c3a1b74c8e9d0a1b2c3d4e5f60";
    private static final String USER_BIZ_2 = "0192f0c3a1b74c8e9d0a1b2c3d4e5f61";

    private JdbcTemplate jdbcTemplate;
    private UserAuthRepository userAuthRepository;
    private LegacyCredentialMigrationRunner runner;

    @BeforeAll
    static void initCrypto() {
        AesGcmUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
        BlindIndexUtil.init(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:migration_" + hashCode() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            // 键集与列类型对齐 Initialize.sql:137-167 / 928-949（批次 U1 · D2）：
            // ① user.user_id 生产是 BINARY(16)（ADR-050），不是 VARCHAR(64) —— 在 VARCHAR 上跑绿
            //    证明不了在真实类型上能跑；
            // ② user 的 4 个唯一键、user_auth 的 2 个唯一键一个不缺：迁移器靠 uk_type_hash 判
            //    「凭证是否已存在」，缺键时这段幂等语义在测试里根本不可达。
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BINARY(16) NOT NULL,
                    phone VARCHAR(20), phone_hash VARCHAR(128), phone_cipher VARCHAR(512), phone_mask VARCHAR(64),
                    email_hash VARCHAR(128),
                    password VARCHAR(100), wx_openid VARCHAR(128),
                    CONSTRAINT uk_user_id UNIQUE (user_id),
                    CONSTRAINT uk_phone_hash UNIQUE (phone_hash),
                    CONSTRAINT uk_email_hash UNIQUE (email_hash),
                    CONSTRAINT uk_wx_openid UNIQUE (wx_openid)
                )""");
            stmt.execute("""
                CREATE TABLE user_auth (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    identity_type VARCHAR(32) NOT NULL,
                    identifier_plain VARCHAR(128),
                    identifier_cipher VARCHAR(512),
                    identifier_hash VARCHAR(128),
                    identifier_mask VARCHAR(64),
                    union_id VARCHAR(64),
                    credential VARCHAR(255),
                    status TINYINT DEFAULT 1,
                    last_login_time TIMESTAMP NULL,
                    create_time TIMESTAMP NULL,
                    update_time TIMESTAMP NULL,
                    CONSTRAINT uk_type_hash UNIQUE (identity_type, identifier_hash),
                    CONSTRAINT uk_type_plain UNIQUE (identity_type, identifier_plain)
                )""");
        }

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(UserAuthMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        // 用 SqlSessionTemplate 取 Mapper（生产同款装配）：它与 Spring 事务管理器协作，
        // 事务内与 JdbcTemplate 共用同一连接；裸 openSession() 会在事务提交后持有已关闭连接。
        SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        jdbcTemplate = new JdbcTemplate(dataSource);
        userAuthRepository = new UserAuthRepositoryImpl(
                sqlSessionTemplate.getMapper(UserAuthMapper.class));
        runner = new LegacyCredentialMigrationRunner(
                jdbcTemplate, new DataSourceTransactionManager(dataSource), userAuthRepository);
    }

    /**
     * 落一行存量 user —— {@code user_id} 按生产形态写 <b>BINARY(16)</b>（32-hex → 原始字节）。
     *
     * <p>把字符串直接塞进 BINARY 列等于绕开真实类型，那样「迁移器在 BINARY(16) 上可行」这句话
     * 就没有依据（改前本列是 {@code VARCHAR(64)}，正是这个缺陷）。
     */
    private void insertLegacyUser(long id, String userIdHex, String phone, String password, String wxOpenid) {
        jdbcTemplate.update("INSERT INTO user (id, user_id, phone, password, wx_openid) VALUES (?, ?, ?, ?, ?)",
                id, IdGenerator.toBytes(userIdHex), phone, password, wxOpenid);
    }

    @Test
    @DisplayName("密码迁移 — user.password → user_auth.credential（PHONE 三字段行），旧列置 NULL")
    void shouldMigratePassword_toLocalCredential() {
        insertLegacyUser(1L, USER_BIZ_1, "13800138000", LEGACY_PASSWORD_HASH, null);

        runner.run();

        UserAuth migrated = userAuthRepository
                .findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000"))
                .orElseThrow();
        assertEquals(1L, migrated.getUserId());
        assertEquals(LEGACY_PASSWORD_HASH, migrated.getCredential());
        assertEquals("138****8000", migrated.getIdentifier().getMask());
        assertNull(jdbcTemplate.queryForObject("SELECT password FROM user WHERE id = 1", String.class),
                "旧密码列必须清空（避免双写残留）");
    }

    @Test
    @DisplayName("密码迁移 — 已有无凭证行（先短信登录）→ 只补 credential，不重复建档")
    void shouldFillCredential_whenRowExistsWithoutCredential() {
        insertLegacyUser(1L, USER_BIZ_1, "13800138000", LEGACY_PASSWORD_HASH, null);
        userAuthRepository.insert(UserAuth.builder()
                .userId(1L)
                .identityType(IdentityTypeEnum.PHONE)
                .identifier(org.icedamericanomall.common.model.EncryptedIdentifier.fromPlain("13800138000"))
                .build());

        runner.run();

        assertEquals(1L, countUserAuthRows());
        UserAuth migrated = userAuthRepository
                .findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000"))
                .orElseThrow();
        assertEquals(LEGACY_PASSWORD_HASH, migrated.getCredential());
    }

    @Test
    @DisplayName("手机号加密 — phone 明文 → cipher/hash/mask 三字段，明文列置 NULL")
    void shouldEncryptPhone_intoThreeFields() {
        insertLegacyUser(1L, USER_BIZ_1, "13800138000", null, null);

        runner.run();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT phone, phone_cipher, phone_hash, phone_mask FROM user WHERE id = 1");
        assertNull(row.get("phone"), "明文列必须清空");
        assertNotNull(row.get("phone_cipher"));
        assertEquals(BlindIndexUtil.hmac("13800138000"), row.get("phone_hash"));
        assertEquals("138****8000", row.get("phone_mask"));
    }

    @Test
    @DisplayName("微信身份 — wx_openid → user_auth（WECHAT，identifier_plain）；旧列保留供人工复核")
    void shouldMigrateWechatIdentity() {
        insertLegacyUser(1L, USER_BIZ_1, null, null, "wx-openid-legacy");

        runner.run();

        UserAuth wechat = userAuthRepository
                .findByIdentity(IdentityTypeEnum.WECHAT, "wx-openid-legacy")
                .orElseThrow();
        assertEquals(1L, wechat.getUserId());
        assertEquals("wx-openid-legacy", wechat.getIdentifierPlain());
        assertEquals("wx-openid-legacy", jdbcTemplate.queryForObject(
                "SELECT wx_openid FROM user WHERE id = 1", String.class), "旧列本轮不清理（人工复核）");
    }

    @Test
    @DisplayName("幂等 — 重复执行不产生重复行、不覆盖既有凭证")
    void shouldBeIdempotent_whenRunTwice() {
        insertLegacyUser(1L, USER_BIZ_1, "13800138000", LEGACY_PASSWORD_HASH, "wx-openid-legacy");

        runner.run();
        runner.run();

        assertEquals(2L, countUserAuthRows(), "PHONE + WECHAT 各一行，重跑不新增");
        UserAuth phoneRow = userAuthRepository
                .findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13800138000"))
                .orElseThrow();
        assertEquals(LEGACY_PASSWORD_HASH, phoneRow.getCredential());
    }

    @Test
    @DisplayName("容错 — 无法解析手机号的密码行只跳过（WARN 不中断），其余行正常迁移")
    void shouldSkipUnresolvableRow_andContinue() {
        insertLegacyUser(1L, USER_BIZ_1, null, LEGACY_PASSWORD_HASH, null);       // 无手机号 → 跳过
        insertLegacyUser(2L, USER_BIZ_2, "13900139000", LEGACY_PASSWORD_HASH, null); // 正常迁移

        runner.run();

        assertNotNull(jdbcTemplate.queryForObject("SELECT password FROM user WHERE id = 1", String.class),
                "无法解析的行保留原列，等待人工处理");
        assertNull(jdbcTemplate.queryForObject("SELECT password FROM user WHERE id = 2", String.class));
        assertTrue(userAuthRepository.findByIdentityHash(
                        IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13900139000")).isPresent(),
                "后续行不受前面失败行影响");
    }

    /**
     * <b>D2 承重用例（本批新增）</b>：{@code user_auth} 的 {@code uk_type_hash} 真的承重
     * —— 同 {@code (identity_type, identifier_hash)} 二次插入由 DB 拦下，并被仓储翻译为
     * {@link DuplicateIdentityException}。
     *
     * <p>为什么重要：迁移器<b>就是</b>靠这条键判「凭证是否已存在」（先查 → 无则建）。
     * 缺键时「已存在」判定与唯一性兜底在测试里都不可达，重跑幂等只是巧合。
     *
     * <p><b>凭什么说它测的是数据库约束</b>：应用层没有任何判重 if ——
     * {@code UserAuthRepositoryImpl#insert} 就是一条 INSERT + 异常翻译。
     * 本断言在<b>无</b> {@code uk_type_hash} 的 H2 DDL 上会失败：第二行插进去（变 2 行），
     * {@code assertThrows} 拿不到异常。
     */
    @Test
    @DisplayName("uk_type_hash — 同 (identity_type, identifier_hash) 二次插入由 DB 拦下 → DuplicateIdentityException")
    void shouldRejectDuplicateIdentityHash_byDatabaseKey() {
        userAuthRepository.insert(phoneAuthRow());

        assertThrows(DuplicateIdentityException.class, () -> userAuthRepository.insert(phoneAuthRow()),
                "若 uk_type_hash 缺失，第二行会插进去 → 本用例变红：凭证唯一性只有 DB 在守");
        assertEquals(1L, countUserAuthRows(), "重复凭证行不得落库");
    }

    /**
     * <b>键集/类型断言（本批新增）</b>：{@code user} 与 {@code user_auth} 的唯一键集、
     * 以及 {@code user.user_id} 的列类型，与 {@code Initialize.sql:139-166 / 943-944} 逐字一致。
     *
     * <p>缺一即红 —— 它是「抄表只抄列、丢了 UNIQUE」「业务键列退化成 VARCHAR」这两类回归的闸门。
     */
    @Test
    @DisplayName("DDL 键集 — user(4) + user_auth(2) 唯一键与 Initialize.sql 一致；user_id 为 BINARY(16)")
    void shouldExposeProductionUniqueKeySet() {
        Map<String, List<String>> userKeys = uniqueKeysOf("user");
        assertEquals(Set.of("uk_user_id", "uk_phone_hash", "uk_email_hash", "uk_wx_openid"), userKeys.keySet());
        assertEquals(List.of("user_id"), userKeys.get("uk_user_id"));
        assertEquals(List.of("phone_hash"), userKeys.get("uk_phone_hash"));
        assertEquals(List.of("email_hash"), userKeys.get("uk_email_hash"));
        assertEquals(List.of("wx_openid"), userKeys.get("uk_wx_openid"));

        Map<String, List<String>> authKeys = uniqueKeysOf("user_auth");
        assertEquals(Set.of("uk_type_hash", "uk_type_plain"), authKeys.keySet());
        assertEquals(List.of("identity_type", "identifier_hash"), authKeys.get("uk_type_hash"),
                "列序必须与生产一致（identity_type 在前）");
        assertEquals(List.of("identity_type", "identifier_plain"), authKeys.get("uk_type_plain"));

        assertEquals("binary", jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_name = 'user' AND column_name = 'user_id'", String.class),
                "user.user_id 必须与生产同为 BINARY(16)（ADR-050）——改前的 VARCHAR(64) 正是本批要修的缺陷");
    }

    /** 一行 PHONE 三字段凭证（同号同哈希，用于撞 uk_type_hash）。 */
    private static UserAuth phoneAuthRow() {
        return UserAuth.builder()
                .userId(1L)
                .identityType(IdentityTypeEnum.PHONE)
                .identifier(org.icedamericanomall.common.model.EncryptedIdentifier.fromPlain("13800138000"))
                .build();
    }

    /**
     * 该表的唯一键 → 有序列集（列顺序取自 {@code KEY_COLUMN_USAGE.ORDINAL_POSITION}）。
     *
     * @param table 表名（本测试 DATABASE_TO_LOWER=TRUE，传小写）
     * @return 键名 → 列名列表
     */
    private Map<String, List<String>> uniqueKeysOf(String table) {
        Map<String, List<String>> keys = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT c.constraint_name AS name, k.column_name AS col "
                        + "FROM information_schema.table_constraints c "
                        + "JOIN information_schema.key_column_usage k "
                        + "ON k.constraint_name = c.constraint_name AND k.table_name = c.table_name "
                        + "WHERE c.table_name = ? AND c.constraint_type = 'UNIQUE' "
                        + "ORDER BY c.constraint_name, k.ordinal_position",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        keys.computeIfAbsent(rs.getString("name"), name -> new ArrayList<>())
                                .add(rs.getString("col")),
                table);
        return keys;
    }

    private long countUserAuthRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_auth", Long.class);
        return count == null ? 0 : count;
    }
}
