// infrastructure/utils/AesGcmUtil.java
package org.icedamericanomall.common.utils.crypto;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.CryptoException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具。
 * <p>密钥从 KMS 加载，不硬编码。
 */
public class AesGcmUtil {

    private static byte[] DATA_KEY;

    public static void init(String base64Key) {
        DATA_KEY = Base64.getDecoder().decode(base64Key);
    }

    public static String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(DATA_KEY, "AES");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.ENCRYPT_ERROR, "加密失败");
        }
    }

    public static String decrypt(String cipherText) {
        try {
            byte[] result = Base64.getDecoder().decode(cipherText);
            byte[] iv = Arrays.copyOfRange(result, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(result, 12, result.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(DATA_KEY, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException(ErrorCode.DECRYPT_ERROR, "解密失败");
        }
    }
}