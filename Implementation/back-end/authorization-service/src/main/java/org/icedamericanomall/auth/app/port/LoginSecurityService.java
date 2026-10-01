package org.icedamericanomall.auth.app.port;

import java.util.function.Supplier;

/**
 * 登录安全防护能力（应用层端口，基础设施层用 Redisson 实现）。
 *
 * <p>职责：登录失败计数 + 超限拦截，成功即清零。
 */
public interface LoginSecurityService {

    /**
     * 在安全防护下执行登录动作（失败计数 / 限流）。
     *
     * @param accountKey  账号标识（手机号 / 用户名 / openid），用作计数键
     * @param loginAction 真正的登录动作
     * @param <T>         登录结果类型
     * @return 登录结果
     */
    <T> T executeWithProtection(String accountKey, Supplier<T> loginAction);
}
