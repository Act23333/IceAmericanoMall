package org.noLazy.common.utils;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Geetest4SignUtil {

    /**
     * 生成极验4.0签名（HMAC-SHA256）
     * @param params 待签名的参数（不包括 sign_token）
     * @param key 极验 key
     * @return 签名字符串（小写十六进制）
     */
    public static String generateSign(Map<String, String> params, String key) {
        // 1. 参数按 key 升序排序
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);
        // 2. 拼接成 "key1=value1&key2=value2..." 形式
        StringBuilder sb = new StringBuilder();
        for (String k : sortedKeys) {
            String v = params.get(k);
            if (v != null && !v.isEmpty()) {
                sb.append(k).append("=").append(v).append("&");
            }
        }
        if (!sb.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1); // 删除末尾 &
        }
        String stringToSign = sb.toString();
        // 3. HMAC-SHA256 签名
        return hmacSha256(stringToSign, key);
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}