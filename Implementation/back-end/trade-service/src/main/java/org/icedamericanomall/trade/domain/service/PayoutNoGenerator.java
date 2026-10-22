package org.icedamericanomall.trade.domain.service;

import org.icedamericanomall.trade.domain.port.PayoutNoFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 出款单号生成器（纯函数，无框架依赖）。
 *
 * <p>格式：{@code 短哈希(16) + "-" + attemptNo}。为什么不是"提现单号 + 序号"：
 * {@code withdrawalNo} 本身已 32 字符（{@code IdGenerator.nextId()} = 16 字节的十六进制），
 * 拼序号必然超过上限。
 */
public final class PayoutNoGenerator {

    private static final int SHORT_HASH_HEX_LENGTH = 16;

    /** 单号长度上限（远比列宽 64 严，避免长序号时逼近列宽）。 */
    public static final int MAX_LENGTH = 32;

    /**
     * 工厂实例：**静态 final，不是 lambda** —— 从形态上杜绝"捕获可变状态"。
     */
    public static final PayoutNoFactory FACTORY = PayoutNoGenerator::generate;

    private PayoutNoGenerator() {
    }

    /**
     * 生成出款单号。
     *
     * @param withdrawalNo 提现单号（非空）
     * @param attemptNo    尝试序号（正整数，只增不减、生成即占号）
     * @return 形如 {@code 9f3ac1b27e0d4a56-1} 的单号
     */
    public static String generate(String withdrawalNo, int attemptNo) {
        if (withdrawalNo == null || withdrawalNo.isBlank()) {
            throw new IllegalArgumentException("withdrawalNo 不能为空：出款单号必须可回溯到提现单");
        }
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo 必须为正数，实际 " + attemptNo);
        }
        String payoutNo = shortHash(withdrawalNo) + '-' + attemptNo;
        if (payoutNo.length() > MAX_LENGTH) {
            throw new IllegalStateException("出款单号超长 " + payoutNo.length() + " > " + MAX_LENGTH);
        }
        return payoutNo;
    }

    /** SHA-256 前 16 位十六进制（小写）。 */
    static String shortHash(String withdrawalNo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(withdrawalNo.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, SHORT_HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}
