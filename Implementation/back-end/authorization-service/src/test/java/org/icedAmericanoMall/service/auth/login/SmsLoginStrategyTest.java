package org.icedAmericanoMall.service.auth.login;

import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.noLazy.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsLoginStrategyTest {

    @Mock
    private UserClient userClient;

    @Mock
    private LoginTokenService loginTokenService;

    @InjectMocks
    private SmsLoginStrategy strategy;

    @Test
    @DisplayName("手机号验证码登录 - 正确验证码 -> 调用短信登录接口")
    void shouldCallSmsLogin_whenPhoneCodeValid() {
        LoginReq request = new LoginReq();
        request.setIdentityType(IdentityTypeEnum.PHONE);
        request.setAccount("13888888888");
        request.setCredentialType(CredentialTypeEnum.SMS_CODE);
        request.setCredential("123456");
        when(userClient.loginBySms(any(SmsLoginReqDTO.class))).thenReturn(new LoginRespDTO());

        strategy.login(request);

        ArgumentCaptor<SmsLoginReqDTO> captor = ArgumentCaptor.forClass(SmsLoginReqDTO.class);
        verify(userClient).loginBySms(captor.capture());
        assertEquals("13888888888", captor.getValue().getPhone());
        assertEquals("123456", captor.getValue().getCode());
    }

    @Test
    @DisplayName("手机号验证码登录 - 验证码格式错误 -> 拒绝请求")
    void shouldThrowBizException_whenSmsCodeInvalid() {
        LoginReq request = new LoginReq();
        request.setIdentityType(IdentityTypeEnum.PHONE);
        request.setAccount("13888888888");
        request.setCredentialType(CredentialTypeEnum.SMS_CODE);
        request.setCredential("abc");

        assertThrows(BizException.class, () -> strategy.login(request));
    }
}
