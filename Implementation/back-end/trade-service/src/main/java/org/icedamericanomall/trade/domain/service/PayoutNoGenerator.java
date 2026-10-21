package org.icedamericanomall.trade.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 出款单号生成器（纯函数，无框架依赖）。
 *
 * <h2>为什么不是「提现单号 + 序号」</h2>
 * 实测 {@code withdrawalNo} 本身已有 32 字符（{@code IdGenerator.nextId()} = 16 字节的十六进制），
 * 再拼序号必然超过 32 字符上限 ⇒ 改用「短哈希 + 序号」。
 *
 * <h2>为什么短哈希不可反解是可接受的</h2>
 * 渠道只把 {@code payoutNo} 当作**不透明幂等键**；回溯提现单靠 {@code withdrawal_payout_attempt}
 * （{@code payout_no} → {@code withdrawal_id}）。反解能力的丢失由尝试表补回。
 *
 * <h2>长度上界（写死并有用例守护）</h2>
 * {@code 16(短哈希) + 1(连字符) + 序号位数}。序号取 {@code Integer.MAX_VALUE}（十位）时仍为二十余字符，
 * 相对上限有三成以上余量 —— 即使序号用满整型也不会溢出。
 */
public final class PayoutNoGenerator {

    /** 短哈希取 SHA-256 的前若干位十六进制。 */
    private static final int SHORT_HASH_HEX_LENGTH = 16;

    /** 单号长度上限（与列宽 64 相比取更严的自限，避免长序号时逼近列宽）。 */
    public static final int MAX_LENGTH = 32;

    private static final char SEPARATOR = '-';

    private PayoutNoGenerator() {
    }

    /**
     * 生成出款单号。
     *
     * @param withdrawalNo 提现单号（非空）
     * @param attemptNo    尝试序号（正整数，只增不减、生成即占号）
     * @return 形如 {@code 9f3ac1b27e0d4a56-1} 的单号
     * @throws IllegalArgumentException 入参非法或结果超长
     */
    public static String generate(String withdrawalNo, int attemptNo) {
        if (withdrawalNo == null || withdrawalNo.isBlank()) {
            throw new IllegalArgumentException("withdrawalNo 不能为空：出款单号必须可回溯到提现单");
        }
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo 必须为正数，实际 " + attemptNo);
        }
        String payoutNo = shortHash(withdrawalNo) + SEPARATOR + attemptNo;
        if (payoutNo.length() > MAX_LENGTH) {
            // 不是防御性安慰：长度上界是数据库列宽与外部渠道字段宽的交集，超了就是错
            throw new IllegalStateException("出款单号超长 " + payoutNo.length() + " > " + MAX_LENGTH);
        }
        return payoutNo;
    }

    /** SHA-256 前 {@value #SHORT_HASH_HEX_LENGTH} 位十六进制（小写）。 */
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
