// domain/auth/model/EncryptedIdentifier.java
package org.icedamericanomall.common.model;


import lombok.Getter;
import org.icedamericanomall.common.utils.crypto.AesGcmUtil;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;

/**
 * 加密标识值对象。
 * <p>敏感标识（手机号、邮箱）统一用三字段模型：
 * <ul>
 *   <li>{@code cipher}：AES-256-GCM 密文，用于解密</li>
 *   <li>{@code hash}：HMAC-SHA256 盲索引，用于查询</li>
 *   <li>{@code mask}：脱敏展示，用于前端</li>
 * </ul>
 */
@Getter
public class EncryptedIdentifier {

    private final String plain;      // 明文（仅内存）
    private final String cipher;     // 密文（持久化）
    private final String hash;       // 盲索引（持久化）
    private final String mask;       // 脱敏（持久化）

    private EncryptedIdentifier(String plain, String cipher, String hash, String mask) {
        this.plain = plain;
        this.cipher = cipher;
        this.hash = hash;
        this.mask = mask;
    }

    /** 从明文创建（注册、绑定时调用） */
    public static EncryptedIdentifier fromPlain(String plain) {
        return new EncryptedIdentifier(
                plain,
                AesGcmUtil.encrypt(plain),
                BlindIndexUtil.hmac(plain),
                DesensitizeUtil.mask(plain)
        );
    }

    /** 从数据库加载（密文、哈希、脱敏已知） */
    public static EncryptedIdentifier fromStorage(String cipher, String hash, String mask) {
        return new EncryptedIdentifier(null, cipher, hash, mask);
    }

    /** 获取明文（需要时解密） */
    public String getPlain() {
        if (plain == null) {
            return AesGcmUtil.decrypt(cipher);
        }
        return plain;
    }
}