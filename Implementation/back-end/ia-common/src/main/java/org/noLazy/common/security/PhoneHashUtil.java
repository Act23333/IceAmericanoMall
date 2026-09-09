package org.noLazy.common.security;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * phone_hash 辅助列 —— SHA-256 固定长 64 字符，布隆预过滤后用 UNIQUE 索引保证唯一。
 */
public final class PhoneHashUtil {

    private PhoneHashUtil() {}

    /** SHA-256(phone) 小写 hex。 */
    public static String sha256Hex(String phone) {
        return DigestUtil.sha256Hex(phone);
    }
}
