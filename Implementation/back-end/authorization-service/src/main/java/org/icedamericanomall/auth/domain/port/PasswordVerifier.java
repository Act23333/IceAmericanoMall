package org.icedamericanomall.auth.domain.port;

/**
 * 密码哈希校验能力（领域层定义，基础设施层用 BCrypt 实现）。
 *
 * <p>存在的意义：把 Spring Security 的 {@code PasswordEncoder} 挡在领域层之外，
 * 领域实体只依赖"能比较哈希"这一业务语义。
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
