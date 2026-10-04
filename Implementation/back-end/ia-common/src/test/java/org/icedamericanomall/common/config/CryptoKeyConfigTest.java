package org.icedamericanomall.common.config;

import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PII 密钥装载单元测试（V5.2 / V5.3）—— 环境注入、开发缺省回退、非法密钥快速失败，
 * 以及生产 profile 下拒绝内置开发密钥（fail-fast）。
 *
 * <p>静态工具（AesGcmUtil / BlindIndexUtil）在装载后可加解密与算盲索引；
 * 这是"资料/凭证分离后 user-service / authorization-service 能落三字段"的运行前提。
 */
@DisplayName("CryptoKeyConfig 单元测试")
class CryptoKeyConfigTest {

    /** 合法 32 字节密钥（Base64） */
    private static final String VALID_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    /** 非生产 profile（默认本地联调）。 */
    private static final String DEV_PROFILES = "dev";

    @Test
    @DisplayName("装载 — 环境变量注入的密钥生效：AES 往返 + HMAC 盲索引可用")
    void shouldInit_whenKeysProvided() {
        new CryptoKeyConfig(VALID_KEY, VALID_KEY, DEV_PROFILES).initCryptoKeys();

        String cipher = AesGcmUtil.encrypt("13800138000");
        assertNotEquals("13800138000", cipher, "密文不得等于明文");
        assertEquals("13800138000", AesGcmUtil.decrypt(cipher));

        String hash = BlindIndexUtil.hmac("13800138000");
        assertEquals(64, hash.length(), "HMAC-SHA256 盲索引为 64 位十六进制");
        assertEquals(hash, BlindIndexUtil.hmac("13800138000"), "同输入盲索引稳定（跨服务可定位）");
    }

    @Test
    @DisplayName("装载 — 非 prod 且密钥未注入：回退内置开发密钥并可用（WARN，不影响启动）")
    void shouldFallbackToDevKey_whenAbsent() {
        new CryptoKeyConfig("", "  ", DEV_PROFILES).initCryptoKeys();

        assertEquals("buyer@example.com", AesGcmUtil.decrypt(AesGcmUtil.encrypt("buyer@example.com")));
        assertEquals(64, BlindIndexUtil.hmac("buyer@example.com").length());
    }

    @Test
    @DisplayName("快速失败 — 非 Base64 密钥：启动即抛（避免半可用状态）")
    void shouldFailFast_whenKeyNotBase64() {
        CryptoKeyConfig config = new CryptoKeyConfig("!!!not-base64!!!", VALID_KEY, DEV_PROFILES);

        assertThrows(IllegalStateException.class, config::initCryptoKeys);
    }

    @Test
    @DisplayName("快速失败 — 解码后非 32 字节：启动即抛（AES-256 密钥长度约束）")
    void shouldFailFast_whenKeyLengthWrong() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        CryptoKeyConfig config = new CryptoKeyConfig(shortKey, VALID_KEY, DEV_PROFILES);

        IllegalStateException exception = assertThrows(IllegalStateException.class, config::initCryptoKeys);
        assertTrue(exception.getMessage().contains("32"));
    }

    @Test
    @DisplayName("快速失败 — prod profile + 未注入密钥：拒绝启动（禁止内置开发密钥上生产）")
    void shouldFailFast_whenProdProfileWithoutKeys() {
        CryptoKeyConfig config = new CryptoKeyConfig("", "", "prod");

        IllegalStateException exception = assertThrows(IllegalStateException.class, config::initCryptoKeys);
        assertTrue(exception.getMessage().contains("prod"));
    }

    @Test
    @DisplayName("快速失败 — 多 profile 含 prod（大小写不敏感）：同样拒绝启动")
    void shouldFailFast_whenProdAmongMultipleProfiles() {
        CryptoKeyConfig config = new CryptoKeyConfig("", "", "dev, PROD ,cn");

        assertThrows(IllegalStateException.class, config::initCryptoKeys);
    }

    @Test
    @DisplayName("prod profile 但密钥已注入 → 正常装载（fail-fast 不得误伤合规配置）")
    void shouldInit_whenProdProfileWithKeys() {
        new CryptoKeyConfig(VALID_KEY, VALID_KEY, "prod").initCryptoKeys();

        assertEquals("prod-user@example.com", AesGcmUtil.decrypt(AesGcmUtil.encrypt("prod-user@example.com")));
    }
}
