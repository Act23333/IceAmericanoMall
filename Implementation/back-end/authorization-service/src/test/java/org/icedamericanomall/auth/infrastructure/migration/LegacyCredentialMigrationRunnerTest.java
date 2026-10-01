package org.icedamericanomall.auth.infrastructure.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.Driver;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.infrastructure.persistence.userauth.UserAuthMapper;
import org.icedamericanomall.auth.infrastructure.persistence.userauth.UserAuthRepositoryImpl;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
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
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            stmt.execute("""
                CREATE TABLE user (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(64),
                    phone VARCHAR(20), phone_hash VARCHAR(128), phone_cipher VARCHAR(512), phone_mask VARCHAR(64),
                    password VARCHAR(100), wx_openid VARCHAR(128)
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
                    update_time TIMESTAMP NULL
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

    private void insertLegacyUser(long id, String userId, String phone, String password, String wxOpenid) {
        jdbcTemplate.update("INSERT INTO user (id, user_id, phone, password, wx_openid) VALUES (?, ?, ?, ?, ?)",
                id, userId, phone, password, wxOpenid);
    }

    @Test
    @DisplayName("密码迁移 — user.password → user_auth.credential（PHONE 三字段行），旧列置 NULL")
    void shouldMigratePassword_toLocalCredential() {
        insertLegacyUser(1L, "u-1", "13800138000", LEGACY_PASSWORD_HASH, null);

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
        insertLegacyUser(1L, "u-1", "13800138000", LEGACY_PASSWORD_HASH, null);
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
        insertLegacyUser(1L, "u-1", "13800138000", null, null);

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
        insertLegacyUser(1L, "u-1", null, null, "wx-openid-legacy");

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
        insertLegacyUser(1L, "u-1", "13800138000", LEGACY_PASSWORD_HASH, "wx-openid-legacy");

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
        insertLegacyUser(1L, "u-1", null, LEGACY_PASSWORD_HASH, null);       // 无手机号 → 跳过
        insertLegacyUser(2L, "u-2", "13900139000", LEGACY_PASSWORD_HASH, null); // 正常迁移

        runner.run();

        assertNotNull(jdbcTemplate.queryForObject("SELECT password FROM user WHERE id = 1", String.class),
                "无法解析的行保留原列，等待人工处理");
        assertNull(jdbcTemplate.queryForObject("SELECT password FROM user WHERE id = 2", String.class));
        assertTrue(userAuthRepository.findByIdentityHash(
                        IdentityTypeEnum.PHONE, BlindIndexUtil.hmac("13900139000")).isPresent(),
                "后续行不受前面失败行影响");
    }

    private long countUserAuthRows() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_auth", Long.class);
        return count == null ? 0 : count;
    }
}
