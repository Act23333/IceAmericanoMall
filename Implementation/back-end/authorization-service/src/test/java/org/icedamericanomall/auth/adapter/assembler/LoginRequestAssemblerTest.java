package org.icedamericanomall.auth.adapter.assembler;

import org.icedamericanomall.auth.adapter.request.WebLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatMiniLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatWebLoginReq;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("装配器 — Request → Command（端点契约与字段映射）")
class LoginRequestAssemblerTest {

    private final LoginRequestAssembler assembler = new LoginRequestAssembler();

    @Test
    @DisplayName("Web 登录 — credentialType 缺省 → PASSWORD，字段逐项映射")
    void shouldDefaultToPassword_whenCredentialTypeAbsent() {
        WebLoginReq req = WebLoginReq.builder()
                .account("alice").credential("pwd").deviceId("device-1").build();

        LoginCommand command = assembler.toWebLoginCommand(req, PlatformEnum.WEB, "1.2.3.4");

        assertEquals(CredentialTypeEnum.PASSWORD, command.credentialType());
        assertEquals("alice", command.identifier());
        assertEquals("pwd", command.credential());
        assertEquals("device-1", command.deviceId());
        assertEquals(PlatformEnum.WEB, command.platform());
        assertEquals("1.2.3.4", command.clientIp());
        assertNull(command.identityType());
        assertNull(command.captchaVerify());
    }

    @Test
    @DisplayName("Web 登录 — 微信类凭证打错端点 → PARAM_ERROR 拒绝")
    void shouldRejectWechatCredential_whenPostedToWebEndpoint() {
        WebLoginReq req = WebLoginReq.builder()
                .account("alice").credential("ignored")
                .credentialType(CredentialTypeEnum.WECHAT_MINI_CODE)
                .build();

        BizException ex = assertThrows(BizException.class,
                () -> assembler.toWebLoginCommand(req, PlatformEnum.WEB, "1.2.3.4"));

        assertTrue(ex.getMessage().contains("不支持的登录凭证类型"));
        assertTrue(ex.getMessage().contains(CredentialTypeEnum.WECHAT_MINI_CODE.getCode()));
    }

    @Test
    @DisplayName("Web 登录 — 携带人机验证参数集 → 厂商中立透传进命令")
    void shouldMapCaptcha_whenParamsProvided() {
        WebLoginReq req = WebLoginReq.builder()
                .account("alice").credential("pwd")
                .captchaProvider("GEETEST")
                .captchaParams(Map.of("lotNumber", "lot", "captchaOutput", "out",
                        "passToken", "token", "genTime", "1700000000"))
                .build();

        LoginCommand command = assembler.toWebLoginCommand(req, PlatformEnum.WEB, "1.2.3.4");

        assertEquals("GEETEST", command.captchaVerify().provider());
        assertEquals("lot", command.captchaVerify().params().get("lotNumber"));
        assertEquals("1.2.3.4", command.captchaVerify().clientIp());
    }

    @Test
    @DisplayName("人机验证 — 四要素全空 → captchaVerify 为 null（废弃 captcha 字段仅告警忽略）")
    @SuppressWarnings("deprecation")
    void shouldSkipCaptcha_whenNothingProvided() {
        WebLoginReq req = WebLoginReq.builder()
                .account("alice").credential("pwd").captcha("legacy-value")
                .build();

        LoginCommand command = assembler.toWebLoginCommand(req, PlatformEnum.WEB, "1.2.3.4");

        assertNull(command.captchaVerify());
    }

    @Test
    @DisplayName("小程序登录 — code 进 credential，phoneCode 进扩展字段")
    void shouldMapWechatMiniCommand() {
        WechatMiniLoginReq req = WechatMiniLoginReq.builder()
                .code("wx-code").phoneCode("phone-code").build();

        LoginCommand command = assembler.toWechatMiniLoginCommand(req,
                PlatformEnum.WECHAT_MINI, "1.2.3.4");

        assertEquals(IdentityTypeEnum.WECHAT_MINI, command.identityType());
        assertEquals(CredentialTypeEnum.WECHAT_MINI_CODE, command.credentialType());
        assertEquals("wx-code", command.credential());
        assertEquals("phone-code", command.extra("phoneCode"));
    }

    @Test
    @DisplayName("微信网页登录 — code 进 credential，state 进扩展字段")
    void shouldMapWechatWebCommand() {
        WechatWebLoginReq req = WechatWebLoginReq.builder()
                .code("oauth-code").state("state-1").build();

        LoginCommand command = assembler.toWechatWebLoginCommand(req,
                PlatformEnum.WECHAT_WEB, "1.2.3.4");

        assertEquals(IdentityTypeEnum.WECHAT, command.identityType());
        assertEquals("oauth-code", command.credential());
        assertEquals("state-1", command.extra("state"));
    }

}
