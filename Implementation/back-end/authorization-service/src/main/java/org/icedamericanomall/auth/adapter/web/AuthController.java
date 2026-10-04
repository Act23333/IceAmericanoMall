package org.icedamericanomall.auth.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.adapter.request.RegisterReq;
import org.icedamericanomall.auth.adapter.request.ResetPasswordReq;
import org.icedamericanomall.auth.adapter.request.WebLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatMiniLoginReq;
import org.icedamericanomall.auth.adapter.request.WechatWebLoginReq;
import org.icedamericanomall.auth.adapter.resolver.DeviceTypeResolver;
import org.icedamericanomall.auth.adapter.response.LoginVO;
import org.icedamericanomall.auth.adapter.response.OAuth2TokenVO;
import org.icedamericanomall.auth.adapter.response.SessionStatusVO;
import org.icedamericanomall.auth.adapter.assembler.LoginRequestAssembler;
import org.icedamericanomall.auth.app.AuthManager;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.command.ResetPasswordCommand;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.utils.IpUtil;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（适配层）—— 只做参数校验、HTTP 上下文解析、委托装配器组装 Command、Result 包装。
 *
 * <p>端点清单（对外契约；邮箱验证码签发已迁出至 verification-service 公开端点
 * {@code POST /api/verification/codes}，见 ADR-016 收尾）：
 * <pre>
 * POST /api/auth/web/login             # Web 登录（密码 / 短信验证码 / 邮箱验证码）
 * POST /api/auth/web/wechat/login      # 微信网页扫码/授权登录
 * POST /api/auth/wechat/mini/login     # 微信小程序登录
 * POST /api/auth/register              # 注册
 * POST /api/auth/reset-password        # 短信验证码重置密码（成功后用户级吊销：令牌版本 + 全端 refresh 会话）
 * POST /api/auth/refresh               # 刷新 Token
 * POST /api/auth/logout                # 登出（吊销 refreshToken + 拉黑当前 accessToken jti）
 * GET  /api/auth/check-session         # 会话检查（空闲超时）
 * </pre>
 *
 * <p>平台类型由服务端根据请求头 / UA 识别（{@link DeviceTypeResolver}），不信任前端传值；
 * 客户端 IP 统一由 ia-common 的 {@link IpUtil} 解析（代理头首个有效段）；
 * Request → Command 装配与 {@code /web/login} 凭证白名单收敛在 {@link LoginRequestAssembler}。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthManager authManager;
    private final DeviceTypeResolver deviceTypeResolver;
    private final LoginRequestAssembler assembler;

    /**
     * 微信小程序登录：前端传 wx.login() 的 code 临时登录凭证，
     * 后端用 code + 小程序 appid + secret 换 openid。
     */
    @PostMapping("/wechat/mini/login")
    public Result<LoginVO> wechatMiniLogin(@RequestBody @Valid WechatMiniLoginReq req,
                                           HttpServletRequest httpRequest) {
        LoginCommand command = assembler.toWechatMiniLoginCommand(req,
                deviceTypeResolver.resolve(httpRequest), IpUtil.getIpAddr(httpRequest));
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
     * 其余取值（微信类等）一律拒绝：请走对应端点（白名单见 {@link LoginRequestAssembler}）。
     */
    @PostMapping("/web/login")
    public Result<LoginVO> webLogin(@RequestBody @Valid WebLoginReq req,
                                    HttpServletRequest httpRequest) {
        LoginCommand command = assembler.toWebLoginCommand(req,
                deviceTypeResolver.resolve(httpRequest), IpUtil.getIpAddr(httpRequest));
        return Result.ok(LoginVO.from(authManager.login(command)));
    }

    /**
     * 微信网页（OAuth2）登录：前端传授权回调 code，复用登录锁 / 失败计数与策略分发。
     */
    @PostMapping("/web/wechat/login")
    public Result<LoginVO> wechatWebLogin(@RequestBody @Valid WechatWebLoginReq req,
                                          HttpServletRequest httpRequest) {
        LoginCommand command = assembler.toWechatWebLoginCommand(req,
                deviceTypeResolver.resolve(httpRequest), IpUtil.getIpAddr(httpRequest));
        return Result.ok(LoginVO.from(authManager.login(command)));
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
     *
     * <p>改密成功后由应用层执行<b>用户级吊销</b>（ADR-031）：推进令牌版本 + 拆除该用户全部 refresh 会话，
     * 使该用户此前签发的全部 access token 与 refresh 会话即刻失效（无需携带 {@code Authorization}，
     * 目标 userId 取自凭证行）。
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
     * 登出：吊销 refreshToken，并把本次请求的 access token 写入网关吊销名单
     * （{@code auth:blacklist:{jti}}，见 ADR-027），使当前登录态即刻失效。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("refresh_token") String refreshToken,
                              @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        authManager.logout(refreshToken, authorizationHeader);
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
}
