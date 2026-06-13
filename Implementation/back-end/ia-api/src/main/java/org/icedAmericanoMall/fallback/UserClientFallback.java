package org.icedAmericanoMall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * @ClassName: UserClientFallback
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/7 1:18
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.fallback
 */
@Slf4j
public class UserClientFallback implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public LoginRespDTO register(RegisterReqDTO request) {
                log.error("用户注册失败", cause);
                return null;
            }

            @Override
            public LoginRespDTO loginByPassword(PasswordLoginReqDTO request) {
                log.error("用户登录失败", cause);
                return null;
            }

            @Override
            public LoginRespDTO loginBySms(SmsLoginReqDTO request) {
                log.error("用户登录失败", cause);
                return null;
            }
        };
    }
}
