package org.icedAmericanoMall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.ResetPasswordReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

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
@Component
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

            @Override
            public LoginRespDTO loginByWechat(org.icedAmericanoMall.dto.WechatLoginReqDTO request) {
                log.error("微信登录失败", cause);
                return null;
            }

            @Override
            public Long countUsers() {
                log.error("获取用户总数失败", cause);
                return 0L;
            }

            @Override
            public Long addPoints(Long userId, int points, int type, String source) {
                log.error("积分发放失败: userId={}, points={}", userId, points, cause);
                return 0L;
            }

            @Override
            public void resetPassword(ResetPasswordReqDTO request) {
                log.error("密码重置失败: phone={}", request != null ? request.getPhone() : null, cause);
            }
        };
    }
}
