package org.icedamericanomall.auth.adapter.assembler;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.adapter.request.BaseLoginReq;
import org.icedamericanomall.auth.adapter.request.WebLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatMiniLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatWebLoginReq;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * 登录 / 验证码 Request → Command 装配器（适配层）。
 *
 * <p>Controller 只解析 HTTP 上下文（平台、客户端 IP），字段映射与端点契约校验收敛于此：
 * <ul>
 *   <li><b>端点契约</b>：{@code /web/login} 只接受账号 + 凭证类登录方式，微信类凭证由
 *       专属端点承担，白名单避免两条入口互相串味（业务策略分发在领域层
 *       {@code LoginStrategyFactory}，它不感知请求来自哪个端点）；</li>
 *   <li><b>人机验证</b>：厂商中立参数集（provider + captchaParams）原样透传，未携带时不校验
 *       （{@code captchaVerify = null}），命中风控时由 {@code CAPTCHA_REQUIRED}
 *       的 payload 驱动前端重试。</li>
 * </ul>
 */
@Slf4j
@Component
public class LoginRequestAssembler {

    /**
     * {@code /web/login} 允许的凭证类型白名单。
     *
     * <p>账号 + 凭证 的登录方式走本端点；微信类凭证有自己的端点（code 换 openid 的语义不同），
     * 白名单避免两条入口互相串味。
     */
    private static final Set<CredentialTypeEnum> WEB_LOGIN_CREDENTIALS =
            EnumSet.of(CredentialTypeEnum.PASSWORD,
                    CredentialTypeEnum.SMS_CODE,
                    CredentialTypeEnum.EMAIL_CODE);

    /** 微信小程序登录命令装配 */
    public LoginCommand toWechatMiniLoginCommand(WechatMiniLoginReq req, PlatformEnum platform,
                                                 String clientIp) {
        return LoginCommand.builder()
                .identityType(IdentityTypeEnum.WECHAT_MINI)
                .credentialType(CredentialTypeEnum.WECHAT_MINI_CODE)
                .credential(req.getCode())
                .extra("phoneCode", req.getPhoneCode())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
    }

    /** Web 登录命令装配（密码 / 短信验证码 / 邮箱验证码，缺省 PASSWORD） */
    public LoginCommand toWebLoginCommand(WebLoginReq req, PlatformEnum platform, String clientIp) {
        return LoginCommand.builder()
                .identityType(req.getIdentityType())
                .identifier(req.getAccount())
                .credentialType(resolveCredentialType(req))
                .credential(req.getCredential())
                .deviceId(req.getDeviceId())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
    }

    /** 微信网页（OAuth2）登录命令装配 */
    public LoginCommand toWechatWebLoginCommand(WechatWebLoginReq req, PlatformEnum platform,
                                                String clientIp) {
        return LoginCommand.builder()
                .identityType(IdentityTypeEnum.WECHAT)
                .credentialType(CredentialTypeEnum.WECHAT_WEB_CODE)
                .credential(req.getCode())
                .extra("state", req.getState())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
    }

    /**
     * 解析登录凭证类型（缺省 PASSWORD），并做白名单校验。
     *
     * @param req Web 登录请求
     * @return 受支持的凭证类型
     * @throws BizException 凭证类型不在白名单内
     */
    private CredentialTypeEnum resolveCredentialType(WebLoginReq req) {
        CredentialTypeEnum credentialType = req.getCredentialType() == null
                ? CredentialTypeEnum.PASSWORD
                : req.getCredentialType();
        if (!WEB_LOGIN_CREDENTIALS.contains(credentialType)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "不支持的登录凭证类型: " + credentialType.getCode());
        }
        return credentialType;
    }

    /**
     * 请求体 → 人机验证凭证（R3：前端提交的极验 v4 参数映射进命令）。
     *
     * <p>未携带任一参数时返回 {@code null}（不校验）；携带则整组映射（厂商中立参数集原样透传，
     * 完整性由 verification-service 按厂商 schema 判定）。
     */
    @SuppressWarnings("deprecation")
    private CaptchaVerifyParam toCaptchaParam(BaseLoginReq req, String clientIp) {
        if (req.getCaptcha() != null && !req.getCaptcha().isBlank()) {
            log.warn("检测到已废弃的 captcha 字段（已忽略），请改用 captchaProvider + captchaParams");
        }
        return CaptchaVerifyParam.of(req.getCaptchaProvider(), req.getCaptchaParams(), clientIp);
    }
}
