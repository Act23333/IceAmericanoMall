package org.icedamericanomall.user.domain.port;

/**
 * 密码哈希校验能力（领域层定义，基础设施层用 BCrypt 实现）。
 *
 * <p>存在的意义：把 Spring Security 的 {@code PasswordEncoder} 挡在领域层之外，
 * 领域实体只依赖"能比较 / 生成哈希"这一业务语义。
 * 与 authorization-service 的同名端口保持一致的契约（严格复制已验证模式）。
 */
public interface PasswordVerifier {

    /**
     * 校验明文密码与已存储的哈希是否匹配。
     *
     * @param rawPassword     用户提交的明文
     * @param encodedPassword 已存储的哈希
     * @return true 表示匹配
     */
    boolean matches(CharSequence rawPassword, String encodedPassword);

    /**
     * 生成密码哈希（注册、改密时使用）。
     *
     * @param rawPassword 明文密码
     * @return 哈希值
     */
    String encode(CharSequence rawPassword);
}
