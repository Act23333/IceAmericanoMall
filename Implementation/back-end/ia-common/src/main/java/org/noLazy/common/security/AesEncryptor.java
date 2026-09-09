package org.noLazy.common.security;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AES-256-CBC PII 静态加密（V2.5）。
 * 仅在 {@code pii.encryption.enabled=true} 时激活，默认关闭（不影响现有查询性能）。
 * 密钥通过环境变量 {@code PII_AES_KEY} 注入（十六进制 64 字符 = 256bit）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pii.encryption.enabled", havingValue = "true")
public class AesEncryptor {

    private final AES aes;

    public AesEncryptor() {
        String keyHex = System.getenv("PII_AES_KEY");
        if (keyHex == null || keyHex.length() != 64) {
            // 未配置密钥时生成临时 key（仅本地 dev，重启后变化 → 不可用于生产）
            keyHex = SecureUtil.md5("icedmall-pii-temp-" + System.currentTimeMillis());
            log.warn("PII_AES_KEY 未设置，使用临时密钥（重启后变化，不可用于生产！）");
        }
        this.aes = SecureUtil.aes(keyHex.getBytes());
    }

    /** AES-256-CBC 加密后 Base64 编码（长度固定，适合索引列）。 */
    public String encrypt(String plaintext) {
        return aes.encryptBase64(plaintext);
    }

    /** AES-256-CBC 解密。 */
    public String decrypt(String ciphertext) {
        return aes.decryptStr(ciphertext);
    }
}
