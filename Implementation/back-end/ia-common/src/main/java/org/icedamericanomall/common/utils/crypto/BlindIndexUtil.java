// infrastructure/utils/BlindIndexUtil.java
package org.icedamericanomall.common.utils.crypto;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.CryptoException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 盲索引工具。
 * <p>密钥与 AES 密钥分离。
 */
public class BlindIndexUtil {

    private static byte[] INDEX_KEY;

    public static void init(String base64Key) {
        INDEX_KEY = Base64.getDecoder().decode(base64Key);
    }

    public static String hmac(String plain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(INDEX_KEY, "HmacSHA256"));
            byte[] hash = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.HASH_ERROR, "hmac哈希失败");
        }
    }
}