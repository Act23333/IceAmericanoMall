package org.icedamericanomall.integration.captcha;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 极验 v4 签名工具（HMAC-SHA256）。
 *
 * <p>规则：参数按 key 升序 → 拼接 {@code k1=v1&k2=v2} → HMAC-SHA256(待签串, key) → 小写十六进制。
 * 空值参数不参与签名。
 */
public final class Geetest4SignUtil {

    private Geetest4SignUtil() {
    }

    /**
     * 生成极验 4.0 签名。
     *
     * @param params 待签名的参数（不包括 sign_token）
     * @param key    极验 key
     * @return 签名字符串（小写十六进制）
     * @throws BizException 签名算法不可用或密钥非法（ErrorCode.THIRD_PARTY_SIGN_ERROR）
     */
    public static String generateSign(Map<String, ?> params, String key) {
        // 1. 参数按 key 升序排序
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);
        // 2. 拼接成 "key1=value1&key2=value2..." 形式
        StringBuilder sb = new StringBuilder();
        for (String k : sortedKeys) {
            Object raw = params.get(k);
            String v = raw == null ? null : String.valueOf(raw);
            if (v != null && !v.isEmpty()) {
                sb.append(k).append("=").append(v).append("&");
            }
        }
        if (!sb.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1); // 删除末尾 &
        }
        // 3. HMAC-SHA256 签名
        return hmacSha256(sb.toString(), key);
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // 09-constraints：禁止裸 RuntimeException，统一 ia-common 异常体系
            throw new BizException(ErrorCode.THIRD_PARTY_SIGN_ERROR, "极验 HMAC-SHA256 签名失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
