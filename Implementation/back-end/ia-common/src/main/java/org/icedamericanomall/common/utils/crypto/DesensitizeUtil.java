// infrastructure/utils/DesensitizeUtil.java
package org.icedamericanomall.common.utils.crypto;

public class DesensitizeUtil {

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