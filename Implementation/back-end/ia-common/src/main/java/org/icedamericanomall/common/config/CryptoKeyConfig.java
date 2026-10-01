package org.icedamericanomall.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * PII 加密密钥装载 —— 启动时初始化 {@link AesGcmUtil}（AES-256-GCM 数据密钥）与
 * {@link BlindIndexUtil}（HMAC-SHA256 盲索引密钥），二密钥分离（ADR-017）。
 *
 * <p><b>密钥来源</b>：生产必须经 KMS / 环境变量注入
 * （{@code IA_CRYPTO_AES_KEY} / {@code IA_CRYPTO_HMAC_KEY}，Base64 编码的 32 字节密钥）；
 * 未注入时使用内置开发密钥并打印 WARN —— 仅为本地联调可用，<b>严禁用于生产</b>
 * （密钥硬编码在生产等于加密失效，属运维红线）。
 *
 * <p><b>为什么必须存在</b>：两个服务的领域模型/持久化都会借助
 * {@code EncryptedIdentifier.fromPlain(...)} 落三字段密文（user-service 的
 * {@code user.phone_cipher/email_cipher}、authorization-service 的
 * {@code user_auth.identifier_cipher}），而 AesGcmUtil / BlindIndexUtil 是静态工具，
 * 未 {@code init} 即调用会抛 {@code CryptoException}。此前仅在单元测试里 init，
 * 运行期没有任何装载点（V5.2 补齐，随资料/凭证分离重构一并落地）。
 *
 * <p>盲索引（HMAC）密钥必须<b>跨服务一致</b>：user-service 用 {@code phone_hash} 定位用户、
 * authorization-service 用 {@code user_auth.identifier_hash} 定位凭证，两侧对同一手机号
 * 必须算出同一盲索引，故同一密钥配置对所有服务生效。
 */
@Slf4j
@Configuration
public class CryptoKeyConfig {

    /** 开发缺省 AES 数据密钥（Base64，32 字节）—— 仅本地联调 */
    private static final String DEV_AES_KEY = "aWNlLWFtZXJpY2Fuby1kZXYtYWVzLWtleS0zMmJ5dGU=";

    /** 开发缺省 HMAC 盲索引密钥（Base64，32 字节）—— 仅本地联调 */
    private static final String DEV_HMAC_KEY = "aWNlLWFtZXJpY2Fuby1kZXYtaG1hYy1pbmRleC1rZXk=";

    private final String aesKey;
    private final String hmacKey;

    public CryptoKeyConfig(@Value("${ia.crypto.aes-key:}") String aesKey,
                           @Value("${ia.crypto.hmac-key:}") String hmacKey) {
        this.aesKey = aesKey;
        this.hmacKey = hmacKey;
    }

    /**
     * 初始化加密工具（AES 密钥与盲索引密钥分离注入）。
     *
     * @throws IllegalStateException 密钥非 Base64 / 非 32 字节（快速失败，避免半可用状态）
     */
    @PostConstruct
    public void initCryptoKeys() {
        boolean devFallback = isBlank(aesKey) || isBlank(hmacKey);
        String resolvedAes = resolveKey(aesKey, DEV_AES_KEY, "AES");
        String resolvedHmac = resolveKey(hmacKey, DEV_HMAC_KEY, "HMAC");
        AesGcmUtil.init(resolvedAes);
        BlindIndexUtil.init(resolvedHmac);
        if (devFallback) {
            log.warn("[crypto][init][dev-fallback] PII 密钥未注入（IA_CRYPTO_AES_KEY / IA_CRYPTO_HMAC_KEY），"
                    + "已使用内置开发密钥 —— 仅限本地联调，生产必须经 KMS/环境变量注入");
        } else {
            log.info("[crypto][init][done] PII 加密密钥已装载（AES-256-GCM + HMAC-SHA256 盲索引）");
        }
    }

    /** 取配置值；为空时回退开发密钥并校验密钥长度。 */
    private static String resolveKey(String configured, String devKey, String keyName) {
        String candidate = isBlank(configured) ? devKey : configured.trim();
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(candidate);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(keyName + " 密钥必须是 Base64 编码", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(keyName + " 密钥解码后必须是 32 字节（当前 " + decoded.length + " 字节）");
        }
        return candidate;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
