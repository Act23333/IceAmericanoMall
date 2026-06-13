package org.noLazy.common.utils;

import java.security.SecureRandom;

public class SmsCodeGenerator {
    private static final ThreadLocal<SecureRandom> THREAD_LOCAL_RANDOM = ThreadLocal.withInitial(SecureRandom::new);
    private static final SecureRandom SECURE_RANDOM = THREAD_LOCAL_RANDOM.get();
    /**
     * 生成不含前导零的固定长度数字验证码（首位非0）。
     * @param length 验证码长度，必须大于0且不超过9（int范围限制）
     * @return 固定长度的数字字符串，首位不为0
     * @throws IllegalArgumentException 当length<=0或length>9时抛出
     */
    public static String generateCode(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        if (length > 9) {
            // 对于超过9位的验证码，int范围已不够，改用字符串拼接方式
            return generateCodeWithLeadingZeros(length);
            // 或者抛出异常：throw new IllegalArgumentException("Length too large for int range");
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
     * @throws IllegalArgumentException 当length<=0时抛出
     */
    public static String generateCodeWithLeadingZeros(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 生成指定长度的数字验证码（可配置是否允许前导零）。
     * @param length          验证码长度，必须大于0
     * @param allowLeadingZero 是否允许前导零
     * @return 固定长度的数字字符串
     * @throws IllegalArgumentException 当length<=0或（不允许前导零且长度超过9）时抛出
     */
    public static String generateCode(int length, boolean allowLeadingZero) {
        if (allowLeadingZero) {
            return generateCodeWithLeadingZeros(length);
        } else {
            if (length > 9) {
                // 不允许前导零且长度超过9时，无法用int表示，改用字符串拼接确保首位非0
                return generateCodeWithoutLeadingZeroByString(length);
            }
            return generateCode(length); // 调用原有的int范围方法
        }
    }

    // 字符串方式生成不含前导零的验证码（支持任意长度）
    private static String generateCodeWithoutLeadingZeroByString(int length) {
        StringBuilder sb = new StringBuilder(length);
        // 第一位不能是0
        sb.append(SECURE_RANDOM.nextInt(9) + 1); // 1-9
        for (int i = 1; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10)); // 0-9
        }
        return sb.toString();
    }
}