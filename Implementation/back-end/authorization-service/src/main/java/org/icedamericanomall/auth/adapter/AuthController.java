package org.icedamericanomall.auth.adapter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.adapter.request.BaseLoginReq;
import org.icedamericanomall.auth.adapter.request.EmailCodeReq;
import org.icedamericanomall.auth.adapter.request.RegisterReq;
import org.icedamericanomall.auth.adapter.request.ResetPasswordReq;
import org.icedamericanomall.auth.adapter.request.WebLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatMiniLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatWebLoginReq;
import org.icedamericanomall.auth.adapter.resolver.DeviceTypeResolver;
import org.icedamericanomall.auth.adapter.response.EmailCodeVO;
import org.icedamericanomall.auth.adapter.response.LoginVO;
import org.icedamericanomall.auth.adapter.response.OAuth2TokenVO;
import org.icedamericanomall.auth.adapter.response.SessionStatusVO;
import org.icedamericanomall.auth.app.AuthManager;
import org.icedamericanomall.auth.app.command.EmailCodeCommand;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.command.ResetPasswordCommand;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.enums.VerificationSceneEnum;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Set;

/**
 * 认证接口（适配层）—— 只做参数校验、Request → Command 组装、Result 包装。
 *
 * <p>端点清单（对外契约，URL 与 JSON 结构保持不变；新增端点见 R3/邮箱登录）：
 * <pre>
 * POST /api/auth/web/login             # Web 登录（密码 / 短信验证码 / 邮箱验证码）
 * POST /api/auth/web/wechat/login      # 微信网页扫码/授权登录
 * POST /api/auth/wechat/mini/login     # 微信小程序登录
 * POST /api/auth/register              # 注册
 * POST /api/auth/email/code            # 🆕 邮箱验证码签发（登录前先发码）
 * POST /api/auth/reset-password        # 短信验证码重置密码
 * POST /api/auth/refresh               # 刷新 Token
 * POST /api/auth/logout                # 登出
 * GET  /api/auth/check-session         # 会话检查（空闲超时）
 * </pre>
 *
 * <p>平台类型由服务端根据请求头 / UA 识别（{@link DeviceTypeResolver}），不信任前端传值。
 *
 * <p>登录凭证类型由请求体 {@code credentialType} 决定（缺省 PASSWORD，白名单见
 * {@link #WEB_LOGIN_CREDENTIALS}）；人机验证参数按极验 v4 四要素增量映射，
 * 未携带时不校验，命中风控时由 {@code CAPTCHA_REQUIRED} 的 payload 驱动前端重试。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

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

    private final AuthManager authManager;
    private final DeviceTypeResolver deviceTypeResolver;

    /**
     * 微信小程序登录：前端传 wx.login() 的 code 临时登录凭证，
     * 后端用 code + 小程序 appid + secret 换 openid。
     */
    @PostMapping("/wechat/mini/login")
    public Result<LoginVO> wechatMiniLogin(@RequestBody @Valid WechatMiniLoginReq req,
                                           HttpServletRequest httpRequest) {
        PlatformEnum platform = deviceTypeResolver.resolve(httpRequest);
        String clientIp = resolveClientIp(httpRequest);
        LoginCommand command = LoginCommand.builder()
                .identityType(IdentityTypeEnum.WECHAT_MINI)
                .credentialType(CredentialTypeEnum.WECHAT_MINI_CODE)
                .credential(req.getCode())
                .extra("phoneCode", req.getPhoneCode())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
        return Result.ok(LoginVO.from(authManager.login(command)));
    }

    /**
     * Web 登录：账号 + 凭证，凭证类型由请求体 {@code credentialType} 决定。
     *
     * <ul>
     *   <li>{@code PASSWORD}（缺省）→ 用户名/手机号/邮箱 + 密码</li>
     *   <li>{@code SMS_CODE} → 手机号 + 短信验证码</li>
     *   <li>{@code EMAIL_CODE} → 邮箱 + 邮箱验证码（首次即注册）</li>
     * </ul>
     * 其余取值（微信类等）一律拒绝：请走对应端点。
     */
    @PostMapping("/web/login")
    public Result<LoginVO> webLogin(@RequestBody @Valid WebLoginReq req,
                                    HttpServletRequest httpRequest) {
        PlatformEnum platform = deviceTypeResolver.resolve(httpRequest);
        String clientIp = resolveClientIp(httpRequest);
        LoginCommand command = LoginCommand.builder()
                .identityType(req.getIdentityType())
                .identifier(req.getAccount())
                .credentialType(resolveCredentialType(req))
                .credential(req.getCredential())
                .deviceId(req.getDeviceId())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
        return Result.ok(LoginVO.from(authManager.login(command)));
    }

    /**
     * 微信网页（OAuth2）登录：前端传授权回调 code，复用登录锁 / 失败计数与策略分发。
     */
    @PostMapping("/web/wechat/login")
    public Result<LoginVO> wechatWebLogin(@RequestBody @Valid WechatWebLoginReq req,
                                         HttpServletRequest httpRequest) {
        PlatformEnum platform = deviceTypeResolver.resolve(httpRequest);
        String clientIp = resolveClientIp(httpRequest);
        LoginCommand command = LoginCommand.builder()
                .identityType(IdentityTypeEnum.WECHAT)
                .credentialType(CredentialTypeEnum.WECHAT_WEB_CODE)
                .credential(req.getCode())
                .extra("state", req.getState())
                .platform(platform)
                .clientIp(clientIp)
                .captchaVerify(toCaptchaParam(req, clientIp))
                .build();
        return Result.ok(LoginVO.from(authManager.login(command)));
    }

    /**
     * 邮箱验证码签发（公开端点）：邮箱 + 场景 → 由 verification-service 生成并经
     * notification-service 投递邮件。
     *
     * <p>重发限流（60 秒窗口 / 错误 5 次作废）在 verification-service 内建，本端点不重复实现；
     * 响应不含验证码明文，只回显脱敏邮箱与有效期。
     */
    @PostMapping("/email/code")
    public Result<EmailCodeVO> sendEmailCode(@Validated @RequestBody EmailCodeReq req,
                                             HttpServletRequest httpRequest) {
        EmailCodeCommand command = EmailCodeCommand.builder()
                .email(req.getEmail())
                .scene(VerificationSceneEnum.fromCode(req.getScene()))
                .clientIp(resolveClientIp(httpRequest))
                .build();
        return Result.ok(EmailCodeVO.from(
                authManager.sendEmailCode(command), DesensitizeUtil.maskEmail(req.getEmail())));
    }

    /**
     * 注册（手机号 + 短信验证码 + 密码），成功后直接签发登录令牌。
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Validated @RequestBody RegisterReq req,
                                    HttpServletRequest httpRequest) {
        RegisterCommand command = RegisterCommand.builder()
                .username(req.getUsername())
                .phone(req.getPhone())
                .password(req.getPassword())
                .code(req.getCode())
                .deviceId(req.getDeviceId())
                .platform(deviceTypeResolver.resolve(httpRequest))
                .build();
        return Result.ok(LoginVO.from(authManager.register(command)));
    }

    /**
     * 找回密码：短信验证码重置密码（公开接口）。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Validated @RequestBody ResetPasswordReq req) {
        authManager.resetPassword(ResetPasswordCommand.builder()
                .phone(req.getPhone())
                .code(req.getCode())
                .newPassword(req.getNewPassword())
                .build());
        return Result.ok("密码重置成功");
    }

    /**
     * 刷新 Token（OAuth2 风格：参数名 {@code refresh_token}）。
     */
    @PostMapping("/refresh")
    public Result<OAuth2TokenVO> refresh(@RequestParam("refresh_token") String refreshToken) {
        return Result.ok(OAuth2TokenVO.from(authManager.refresh(refreshToken)));
    }

    /**
     * 登出：吊销 refreshToken。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("refresh_token") String refreshToken) {
        authManager.logout(refreshToken);
        return Result.ok("登出成功");
    }

    /**
     * 检查会话（空闲超时）— 验证 access_token 有效性，返回剩余 TTL。
     * 前端用来判断空闲超时（30 分钟无操作后 token 过期 → 需重新登录）。
     */
    @GetMapping("/check-session")
    public Result<SessionStatusVO> checkSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Result.ok(SessionStatusVO.from(authManager.checkSession(authHeader)));
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
     * <p>未携带任一参数时返回 {@code null}（不校验）；携带则整组映射，
     * 由应用层判定"是否启用 / 是否完整 / 是否通过"。
     *
     * @param req      登录请求（各端公共字段）
     * @param clientIp 客户端 IP
     * @return 人机验证凭证，或 null
     */
    @SuppressWarnings("deprecation")
    private CaptchaVerifyParam toCaptchaParam(BaseLoginReq req, String clientIp) {
        if (req.getCaptcha() != null && !req.getCaptcha().isBlank()) {
            log.warn("检测到已废弃的 captcha 字段（已忽略），请改用 lotNumber/captchaOutput/passToken/genTime");
        }
        CaptchaVerifyParam param = new CaptchaVerifyParam(req.getCaptchaProvider(), req.getLotNumber(),
                req.getCaptchaOutput(), req.getPassToken(), req.getGenTime(), clientIp);
        return param.isProvided() ? param : null;
    }

    /**
     * 解析客户端真实 IP（网关透传 X-Forwarded-For 时取第一段）。
     * <p>风控维度需要真实 IP，不能直接信任 remoteAddr（Nginx/网关之后会变成内网地址）。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
