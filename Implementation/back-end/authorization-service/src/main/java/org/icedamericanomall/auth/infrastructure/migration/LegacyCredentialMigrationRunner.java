package org.icedamericanomall.auth.infrastructure.migration;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.domain.model.userauth.UserAuth;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.model.EncryptedIdentifier;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 存量凭证/资料迁移器（V5.2 一次性工具，ADR-017）—— auth 侧启动执行，默认关闭。
 *
 * <p>解决的问题：资料/凭证分离前的存量数据分布在 {@code user} 表的历史列里
 * （{@code password} 明文哈希列、{@code phone} 明文、{@code wx_openid}），
 * 而 V5.2 后的唯一权威是：密码/微信身份 → {@code user_auth}（auth 上下文），
 * 手机号 → {@code user.phone_cipher/phone_hash/phone_mask}（三字段 PII）。
 *
 * <p><b>执行方式</b>：{@code auth.legacy-migration.enabled=true}（环境变量
 * {@code AUTH_LEGACY_MIGRATION_ENABLED}）后启动一次；幂等可重跑，
 * <b>逐条独立事务</b>（单行失败只 WARN 并继续，不中断整批）。
 *
 * <p>三个迁移步骤（顺序有依赖：密码迁移依赖手机号可解析）：
 * <ol>
 *   <li><b>密码迁移</b>：{@code user.password} 非空 → 按手机号盲索引找 {@code user_auth}
 *       （无则建 PHONE 三字段行，有则只补 credential）→ 置 {@code user.password=NULL}；</li>
 *   <li><b>手机号加密</b>：{@code user.phone} 明文 → 写
 *       {@code phone_cipher/phone_hash/phone_mask} → 置 {@code user.phone=NULL}；</li>
 *   <li><b>微信身份</b>：{@code user.wx_openid} 非空且无 WECHAT 凭证行 → 建行
 *       （{@code identifier_plain=openid}）。{@code wx_openid} 列本轮不置空，
 *       保留供人工复核（列本身已是 deprecated，代码不读写）。</li>
 * </ol>
 *
 * <p>旧列（{@code password}/{@code wx_openid}/{@code role_type}/{@code phone}）本轮均不删，
 * 人工复核后由后续 DDL 走删列流程。
 *
 * <p>前置：PII 密钥必须已装载（ia-common {@code CryptoKeyConfig} / 环境变量注入），
 * 否则本迁移器会在启动时构造 {@link EncryptedIdentifier} 失败（逐行 WARN，不中断启动）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "auth.legacy-migration.enabled", havingValue = "true")
public class LegacyCredentialMigrationRunner implements CommandLineRunner {

    private static final String TAG = "[auth][legacy-migration]";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final UserAuthRepository userAuthRepository;

    public LegacyCredentialMigrationRunner(JdbcTemplate jdbcTemplate,
                                           PlatformTransactionManager transactionManager,
                                           UserAuthRepository userAuthRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userAuthRepository = userAuthRepository;
    }

    @Override
    public void run(String... args) {
        log.info("{}[start] 存量迁移开始（幂等可重跑）", TAG);
        int passwords = runStep("password", this::loadPasswordRows, this::migratePasswordRow);
        int phones = runStep("phone", this::loadPhoneRows, this::migratePhoneRow);
        int wechats = runStep("wechat", this::loadWechatRows, this::migrateWechatRow);
        log.info("{}[done] 密码迁移={} 条, 手机号加密={} 条, 微信身份={} 条", TAG, passwords, phones, wechats);
    }

    // ==================== 步骤 1：密码凭证迁移 ====================

    /** user.password 非空的行（含手机号解析所需的列）。 */
    private List<Map<String, Object>> loadPasswordRows() {
        return jdbcTemplate.queryForList("SELECT id, user_id, phone, phone_hash, phone_cipher, password FROM user "
                + "WHERE password IS NOT NULL AND password <> ''");
    }

    /**
     * 单行密码迁移（在独立事务内执行；返回 true 表示本行已处理）。
     *
     * <p>幂等：处理完成即置 {@code user.password=NULL}，重跑不再命中本行。
     */
    private boolean migratePasswordRow(Map<String, Object> row) {
        Long userId = longOf(row.get("id"));
        String credential = str(row.get("password"));
        String plainPhone = resolvePlainPhone(row);
        if (plainPhone == null) {
            log.warn("{}[password][skip] userId={} 无法解析手机号（phone/phone_cipher 均缺失），保留原密码列", TAG, userId);
            return false;
        }
        String hash = hashOf(plainPhone, str(row.get("phone_hash")));

        Optional<UserAuth> existing = userAuthRepository.findByIdentityHash(IdentityTypeEnum.PHONE, hash);
        if (existing.isPresent()) {
            UserAuth userAuth = existing.get();
            if (userAuth.getUserId() != null && !userAuth.getUserId().equals(userId)) {
                log.warn("{}[password][skip] userId={} 盲索引命中他人凭证 userId={}，人工核查",
                        TAG, userId, userAuth.getUserId());
                return false;
            }
            if (isBlank(userAuth.getCredential())) {
                userAuth.updatePassword(credential);
                userAuthRepository.updateByUserIdAndType(userAuth);
                log.info("{}[password][fill-credential] userId={}", TAG, userId);
            } else {
                log.info("{}[password][exists] userId={} 凭证已存在，仅清理旧列", TAG, userId);
            }
        } else {
            userAuthRepository.insert(UserAuth.builder()
                    .userId(userId)
                    .identityType(IdentityTypeEnum.PHONE)
                    .identifier(EncryptedIdentifier.fromPlain(plainPhone))
                    .credential(credential)
                    .build());
            log.info("{}[password][create] userId={}", TAG, userId);
        }

        jdbcTemplate.update("UPDATE user SET password = NULL WHERE id = ?", userId);
        return true;
    }

    // ==================== 步骤 2：手机号 PII 加密 ====================

    /** user.phone 明文非空的行。 */
    private List<Map<String, Object>> loadPhoneRows() {
        return jdbcTemplate.queryForList("SELECT id, user_id, phone FROM user "
                + "WHERE phone IS NOT NULL AND phone <> ''");
    }

    /** 单行手机号加密：明文 → 三字段（cipher + hash + mask）→ 清空明文列。 */
    private boolean migratePhoneRow(Map<String, Object> row) {
        Long userId = longOf(row.get("id"));
        String plainPhone = str(row.get("phone"));
        if (plainPhone == null) {
            return false;
        }
        EncryptedIdentifier identifier = EncryptedIdentifier.fromPlain(plainPhone);
        jdbcTemplate.update("UPDATE user SET phone_cipher = ?, phone_hash = ?, phone_mask = ?, phone = NULL "
                + "WHERE id = ?", identifier.getCipher(), identifier.getHash(), identifier.getMask(), userId);
        log.info("{}[phone][encrypted] userId={}", TAG, userId);
        return true;
    }

    // ==================== 步骤 3：微信身份迁移 ====================

    /** user.wx_openid 非空的行。 */
    private List<Map<String, Object>> loadWechatRows() {
        return jdbcTemplate.queryForList("SELECT id, user_id, wx_openid FROM user "
                + "WHERE wx_openid IS NOT NULL AND wx_openid <> ''");
    }

    /** 单行微信身份迁移：无 WECHAT 凭证行则建行（identifier_plain=openid）。 */
    private boolean migrateWechatRow(Map<String, Object> row) {
        Long userId = longOf(row.get("id"));
        String openid = str(row.get("wx_openid"));
        if (openid == null) {
            return false;
        }
        Optional<UserAuth> existing = userAuthRepository.findByIdentity(IdentityTypeEnum.WECHAT, openid);
        if (existing.isPresent()) {
            log.info("{}[wechat][exists] userId={} 凭证已存在，跳过", TAG, userId);
            return true;
        }
        userAuthRepository.insert(UserAuth.builder()
                .userId(userId)
                .identityType(IdentityTypeEnum.WECHAT)
                .identifierPlain(openid)
                .build());
        log.info("{}[wechat][create] userId={}", TAG, userId);
        return true;
    }

    // ==================== 通用执行框架 ====================

    /**
     * 逐条执行迁移（逐条独立事务；单条失败 WARN 不中断）。
     *
     * @return 成功处理的条数
     */
    private int runStep(String stepName,
                        java.util.function.Supplier<List<Map<String, Object>>> loader,
                        java.util.function.Function<Map<String, Object>, Boolean> rowMigrator) {
        List<Map<String, Object>> rows;
        try {
            rows = loader.get();
        } catch (Exception e) {
            log.warn("{}[{}][load][fail] 跳过本步骤（表/列可能不存在）", TAG, stepName, e);
            return 0;
        }
        int migrated = 0;
        for (Map<String, Object> row : rows) {
            try {
                Boolean changed = transactionTemplate.execute(status -> rowMigrator.apply(row));
                if (Boolean.TRUE.equals(changed)) {
                    migrated++;
                }
            } catch (Exception e) {
                log.warn("{}[{}][row][fail] rowId={}，跳过本行（可重跑）", TAG, stepName, row.get("id"), e);
            }
        }
        return migrated;
    }

    // ==================== 工具方法 ====================

    /** 解析明文手机号：优先明文列，其次解密密文列（兼容"手机号已加密、密码未迁移"的中间态）。 */
    private String resolvePlainPhone(Map<String, Object> row) {
        String plain = str(row.get("phone"));
        if (plain != null) {
            return plain;
        }
        String cipher = str(row.get("phone_cipher"));
        if (cipher == null) {
            return null;
        }
        try {
            return AesGcmUtil.decrypt(cipher);
        } catch (Exception e) {
            return null;
        }
    }

    /** 盲索引：优先复用既有 phone_hash，缺失时现算（与 user-service / auth 共用同一 HMAC 密钥）。 */
    private String hashOf(String plainPhone, String storedHash) {
        return storedHash != null ? storedHash : BlindIndexUtil.hmac(plainPhone);
    }

    private static Long longOf(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
