package org.icedamericanomall.auth.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.port.LoginSecurityService;
import org.icedamericanomall.auth.app.port.risk.LoginFailCountService;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 登录安全防护实现（基础设施层）—— 失败计数 + 超限拦截。
 *
 * <p>失败计数统一走 {@link LoginFailCountService}（单一数据源，与风控评估共用），
 * 登录成功后清零；计数键与过期窗口的定义都收敛在该端口实现里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSecurityServiceImpl implements LoginSecurityService {

    /** 超过该失败次数则暂时拒绝登录 */
    private static final int MAX_FAIL_COUNT = 5;

    private final LoginFailCountService loginFailCountService;

    @Override
    public <T> T executeWithProtection(String accountKey, Supplier<T> loginAction) {
        if (accountKey != null && loginFailCountService.getFailCount(accountKey) >= MAX_FAIL_COUNT) {
            log.warn("登录失败次数超限，暂时拒绝: account={}, failCount={}",
                    accountKey, loginFailCountService.getFailCount(accountKey));
            throw new BizException(ErrorCode.FREQUENT_ERROR, "登录失败次数过多，请稍后再试");
        }
        try {
            T result = loginAction.get();
            if (accountKey != null) {
                loginFailCountService.clear(accountKey);
            }
            return result;
        } catch (BizException e) {
            if (accountKey != null) {
                loginFailCountService.increment(accountKey);
            }
            throw e;
        }
    }
}
