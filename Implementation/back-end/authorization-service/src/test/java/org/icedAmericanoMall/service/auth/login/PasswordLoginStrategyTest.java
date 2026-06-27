package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordLoginStrategyTest {

    @Mock
    private UserClient userClient;

    @Mock
    private LoginTokenService loginTokenService;

    @InjectMocks
    private PasswordLoginStrategy strategy;

    @Test
    @DisplayName("用户名密码登录 - 正确凭证 -> 调用用户服务并返回Token")
    void shouldReturnToken_whenUsernamePasswordValid() {
        LoginReq request = loginReq(IdentityTypeEnum.USERNAME, "tom_1", "Aa123456!");
        LoginRespDTO loginResp = new LoginRespDTO(1L, "tom_1", "13888888888", "ROLE_USER");
        OAuth2TokenResp tokenResp = new OAuth2TokenResp();
        when(userClient.loginByPassword(any(PasswordLoginReqDTO.class))).thenReturn(loginResp);
        when(loginTokenService.createLoginResponse(loginResp)).thenReturn(tokenResp);

        OAuth2TokenResp result = strategy.login(request);

        ArgumentCaptor<PasswordLoginReqDTO> captor = ArgumentCaptor.forClass(PasswordLoginReqDTO.class);
        verify(userClient).loginByPassword(captor.capture());
        assertEquals("tom_1", captor.getValue().getUsername());
        assertEquals("Aa123456!", captor.getValue().getPassword());
        assertSame(tokenResp, result);
    }

    @Test
    @DisplayName("手机号密码登录 - 正确凭证 -> 使用手机号查询")
    void shouldUsePhone_whenPhonePasswordValid() {
        LoginReq request = loginReq(IdentityTypeEnum.PHONE, "13888888888", "Aa123456!");
        LoginRespDTO loginResp = new LoginRespDTO(1L, "ice_user", "13888888888", "ROLE_USER");
        when(userClient.loginByPassword(any(PasswordLoginReqDTO.class))).thenReturn(loginResp);
        when(loginTokenService.createLoginResponse(loginResp)).thenReturn(new OAuth2TokenResp());

        strategy.login(request);

        ArgumentCaptor<PasswordLoginReqDTO> captor = ArgumentCaptor.forClass(PasswordLoginReqDTO.class);
        verify(userClient).loginByPassword(captor.capture());
        assertEquals("13888888888", captor.getValue().getPhone());
    }

    @Test
    @DisplayName("密码登录 - 密码格式错误 -> 拒绝请求")
    void shouldThrowBizException_whenPasswordInvalid() {
        LoginReq request = loginReq(IdentityTypeEnum.USERNAME, "tom_1", "123456");

        assertThrows(BizException.class, () -> strategy.login(request));
    }

    private LoginReq loginReq(IdentityTypeEnum identityType, String account, String credential) {
        LoginReq request = new LoginReq();
        request.setIdentityType(identityType);
        request.setAccount(account);
        request.setCredentialType(CredentialTypeEnum.PASSWORD);
        request.setCredential(credential);
        return request;
    }
}
