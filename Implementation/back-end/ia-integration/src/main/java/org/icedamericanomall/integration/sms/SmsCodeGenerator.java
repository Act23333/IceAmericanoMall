package org.icedamericanomall.integration.sms;

import java.security.SecureRandom;

/**
 * 数字验证码生成器 —— 登录 / 注册 / 找回密码链路的唯一验证码来源。
 *
 * <p>随机源为 {@link SecureRandom}（密码学安全），不使用 {@code Math.random()} /
 * {@code Random}，避免验证码可被预测。
 *
 * <p>契约（三个方法各不相同，勿混用）：
 * <ul>
 *   <li>{@link #generateCode(int)} —— <b>首位非 0</b>，任意长度；</li>
 *   <li>{@link #generateCodeWithLeadingZeros(int)} —— <b>允许首位为 0</b>，任意长度；</li>
 *   <li>{@link #generateCode(int, boolean)} —— 由 {@code allowLeadingZero} 决定。</li>
 * </ul>
 */
public class SmsCodeGenerator {

    /** 每位数字 0-9 的取值范围（`nextInt` 语义为左闭右开）。 */
    private static final int DIGIT_BOUND = 10;
    /** 首位数字取值范围 1-9 的数量。 */
    private static final int NON_ZERO_FIRST_DIGIT_BOUND = 9;

    /**
     * 每个线程独立的 {@link SecureRandom}。
     *
     * <p>注意：{@code SECURE_RANDOM} 是<b>类加载线程</b>的实例，静态持有后即被所有线程共享。
     * 之所以能安全工作，是因为 {@link SecureRandom} 本身线程安全（{@code nextBytes} 内部同步）；
     * 这里保留 ThreadLocal 只是为了兼容既有写法，不做热路径性能优化依据。
     */
    private static final ThreadLocal<SecureRandom> THREAD_LOCAL_RANDOM = ThreadLocal.withInitial(SecureRandom::new);
    private static final SecureRandom SECURE_RANDOM = THREAD_LOCAL_RANDOM.get();

    /**
     * 生成不含前导零的固定长度数字验证码（首位非0）。
     * @param length 验证码长度，必须大于0；大于9 时自动走字符串拼接分支
     * @return 固定长度的数字字符串，首位不为0
     * @throws IllegalArgumentException 当 length&lt;=0 时抛出
     */
    public static String generateCode(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        if (length > 9) {
            // 对于超过9位的验证码，int范围已不够，改用字符串拼接方式。
            // V5.2 修正：原先误调用 generateCodeWithLeadingZeros（允许首位为 0），
            // 与本方法「首位非 0」的契约、以及调用方 verification-service
            // CodeGeneratorAdapter 的文档承诺相矛盾。
            return generateCodeWithoutLeadingZeroByString(length);
        }

        int min = (int) Math.pow(10, length - 1);
        int max = (int) Math.pow(10, length) - 1;
        int code = SECURE_RANDOM.nextInt(max - min + 1) + min;
        return String.valueOf(code);
    }

    /**
     * 生成允许前导零的固定长度数字验证码（每位0-9）。
     * @param length 验证码长度，必须大于0
     * @return 固定长度的数字字符串，可能以0开头
     * @throws IllegalArgumentException 当length&lt;=0时抛出
     */
    public static String generateCodeWithLeadingZeros(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(DIGIT_BOUND));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的数字验证码（可配置是否允许前导零）。
     * @param length          验证码长度，必须大于0
     * @param allowLeadingZero 是否允许前导零
     * @return 固定长度的数字字符串
     * @throws IllegalArgumentException 当 length&lt;=0 时抛出
     */
    public static String generateCode(int length, boolean allowLeadingZero) {
        if (allowLeadingZero) {
            return generateCodeWithLeadingZeros(length);
        }
        if (length > 9) {
            // 不允许前导零且长度超过9时，无法用int表示，改用字符串拼接确保首位非0
            return generateCodeWithoutLeadingZeroByString(length);
        }
        return generateCode(length); // 调用原有的int范围方法
    }

    // 字符串方式生成不含前导零的验证码（支持任意长度）
    private static String generateCodeWithoutLeadingZeroByString(int length) {
        StringBuilder sb = new StringBuilder(length);
        // 第一位不能是0
        sb.append(SECURE_RANDOM.nextInt(NON_ZERO_FIRST_DIGIT_BOUND) + 1); // 1-9
        for (int i = 1; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(DIGIT_BOUND)); // 0-9
        }
        return sb.toString();
    }
}