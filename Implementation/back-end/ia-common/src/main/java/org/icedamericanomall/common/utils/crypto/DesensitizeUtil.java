// infrastructure/utils/DesensitizeUtil.java
package org.icedamericanomall.common.utils.crypto;

/**
 * 展示用脱敏工具（掩码规则的<b>唯一出口</b>，ia-common 共享）。
 *
 * <p>与加解密/盲索引的分工：本类只做「掩码展示」，不碰密钥、不落库。
 * 三字段模型（cipher + hash + mask）中的 {@code mask} 列即由本类产出；
 * 加解密见 {@link AesGcmUtil}，盲索引见 {@link BlindIndexUtil}。
 *
 * <p><b>掩码规则与「不泄露原串长度」约定</b>（改动会破坏既有测试与落库数据，勿轻改）：
 * <ul>
 *   <li>{@link #maskPhone(String)} —— {@code 13800138000 → 138****8000}</li>
 *   <li>{@link #maskEmail(String)} —— {@code abc@example.com → ab***@example.com}</li>
 *   <li>{@link #maskAccount(String)} —— {@code 6222021234567890123 → ****0123}（账号/卡号类，尾 4）</li>
 *   <li>{@link #mask(String)} —— 按标识形态自动选择手机号 / 邮箱规则（日志脱敏入口）</li>
 * </ul>
 * 所有方法对 {@code null} 一律原样返回（保留「未填写」语义），不做 NPE 兜底。
 */
public class DesensitizeUtil {

    /** 账号类掩码占位（固定 4 星，不泄露原串长度）。 */
    private static final String MASK = "****";

    /** 账号类掩码保留的尾部字符数。 */
    private static final int ACCOUNT_KEEP_TAIL_LENGTH = 4;

    /** 手机号脱敏：13800138000 → 138****8000 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 邮箱脱敏：abc@example.com → ab***@example.com */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        if (name.length() <= 2) return "***@" + parts[1];
        return name.substring(0, 2) + "***@" + parts[1];
    }

    /**
     * 账号类脱敏（银行卡号 / 收款账号 / 任意长数字账号）：保留末 4 位，前缀以固定 4 星掩去。
     *
     * <p>语义逐字符锁定（由 {@code DesensitizeUtilTest} 保护，trade-service 提现出参依赖）：
     * <pre>
     * "6222021234567890123" → "****0123"   （长度 &gt; 4 → 尾 4 保留）
     * "1234"                → "****"       （长度 ≤ 4 → 全遮，不泄露原串长度）
     * null                  → null         （保留「未填写」语义）
     * ""                    → ""           （保留空串语义）
     * </pre>
     *
     * <p>由 trade-service 的 {@code SensitiveMaskUtil}（V5.4 收口）归并而来：账号类掩码规则
     * 与手机号/邮箱掩码同属「展示层掩码」一族，必须只有一处定义，避免商家端/管理端两个
     * 出参口径漂移。
     *
     * @param account 原始账号，可为 null
     * @return 掩码串；入参为 null / 空串时原样返回
     */
    public static String maskAccount(String account) {
        if (account == null || account.isEmpty()) {
            return account;
        }
        if (account.length() <= ACCOUNT_KEEP_TAIL_LENGTH) {
            return MASK;
        }
        return MASK + account.substring(account.length() - ACCOUNT_KEEP_TAIL_LENGTH);
    }

    /** 通用脱敏：根据标识类型自动选择 */
    public static String mask(String identifier) {
        if (identifier == null) return null;
        if (identifier.matches("^1[3-9]\\d{9}$")) {
            return maskPhone(identifier);
        }
        if (identifier.contains("@")) {
            return maskEmail(identifier);
        }
        return identifier;
    }
}