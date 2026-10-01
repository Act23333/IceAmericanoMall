package org.icedamericanomall.auth.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.auth.app.command.EmailCodeCommand;
import org.icedamericanomall.auth.app.command.LoginCommand;
import org.icedamericanomall.auth.app.command.RegisterCommand;
import org.icedamericanomall.auth.app.command.ResetPasswordCommand;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;
import org.icedamericanomall.auth.app.dto.LoginDTO;
import org.icedamericanomall.auth.app.dto.SessionCheckDTO;
import org.icedamericanomall.auth.app.dto.TokenGenerateParam;
import org.icedamericanomall.auth.app.dto.TokenPair;
import org.icedamericanomall.auth.app.port.CaptchaService;
import org.icedamericanomall.auth.app.port.LoginSecurityService;
import org.icedamericanomall.auth.app.port.LoginTokenService;
import org.icedamericanomall.auth.app.port.RiskControlService;
import org.icedamericanomall.auth.app.port.VerificationCodeIssueService;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategy;
import org.icedamericanomall.auth.domain.ability.auth.LoginStrategyFactory;
import org.icedamericanomall.auth.domain.ability.risk.RiskHandlerFactory;
import org.icedamericanomall.auth.domain.enums.VerificationChannelEnum;
import org.icedamericanomall.auth.domain.gateway.UserAccountGateway;
import org.icedamericanomall.auth.domain.model.EmailAddress;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.auth.domain.model.UserAuth;
import org.icedamericanomall.auth.domain.port.PasswordVerifier;
import org.icedamericanomall.auth.domain.port.VerificationCodeService;
import org.icedamericanomall.auth.domain.repository.UserAuthRepository;
import org.icedamericanomall.auth.domain.service.UserAuthProvisioner;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 认证用例编排器（应用层）。
 *
 * <p>职责：编排用例 —— 人机验证 → 风控评估 → 风控处置 → 领域策略登录 → 签发令牌；
 * 注册/重置密码在本服务本地完成凭证写入（凭证唯一所有权方，ADR-017）。
 * 业务规则在领域层（LoginStrategy / UserAuth / UserAuthProvisioner），
 * 远程调用在基础设施层，本类只做顺序编排与跨层转换，<b>不写业务规则</b>。
 *
 * <p>事务边界：本类所有用例都是"单条本地写入 + 远程调用"或纯远程调用，
 * 本地写入天然原子（单条 INSERT/UPDATE），因此<b>不声明 {@code @Transactional}</b>：
 * 事务内调用 Feign 会长时间占用数据库连接，且远程失败无法回滚本地提交。
 * 跨上下文一致性由"远端幂等 + 本地唯一索引 + 失败后重放"保证；
 * 本项目未引入 MQ，故不采用本地消息表 / 事务消息方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthManager {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 会话空闲超时（分钟），仅用于前端提示 */
    private static final long IDLE_TIMEOUT_MINUTES = 30;

    private final RiskHandlerFactory riskHandlerFactory;
    private final LoginStrategyFactory strategyFactory;
    private final UserAccountGateway userAccountGateway;
    private final UserAuthProvisioner userAuthProvisioner;
    private final UserAuthRepository userAuthRepository;
    private final PasswordVerifier passwordVerifier;
    private final LoginSecurityService loginSecurityService;
    private final LoginTokenService loginTokenService;
    private final RiskControlService riskControlService;
    private final CaptchaService captchaService;
    private final VerificationCodeService verificationCodeService;
    private final VerificationCodeIssueService verificationCodeIssueService;

    /**
     * 登录用例。
     *
     * @param command 登录命令（各端统一入参）
     * @return 登录结果（Token + userId）
     */
    public LoginDTO login(LoginCommand command) {
        // 1. 人机验证（仅当已启用 + 前端携带验证凭证）
        boolean captchaVerified = verifyCaptchaIfProvided(command);

        LoginSpec spec = command.toSpec();

        // 2. 风控评估（携带"本次是否已通过人机验证"，供风控处置判断是否仍需强制验证）
        RiskAssessment assessment = riskControlService.assess(spec).withCaptchaVerified(captchaVerified);

        // 3. 风控处置（阻断 / 要求验证时抛异常）
        riskHandlerFactory.handleRisk(assessment);

        // 4. 领域策略登录（失败计数 / 限流保护）
        LoginStrategy strategy = strategyFactory.getStrategy(spec.identityType(), spec.credentialType());
        UserAuth userAuth = loginSecurityService.executeWithProtection(
                spec.identifier(), () -> strategy.login(spec));

        // 5. 签发令牌
        TokenPair tokenPair = loginTokenService.createLoginResponse(buildTokenParam(
                userAuth.getUserId(), command));
        log.info("登录成功: userId={}, identityType={}, credentialType={}, platform={}",
                userAuth.getUserId(), spec.identityType(), spec.credentialType(), spec.platform());
        return LoginDTO.of(userAuth, tokenPair);
    }

    /**
     * 注册用例（手机号 + 短信验证码 + 密码）—— 凭证唯一所有权归本服务的闭环（ADR-017）。
     *
     * <p>流程（修复"注册后密码登录失败"的凭证脑裂）：
     * <ol>
     *   <li>本地校验并消费短信验证码（verification-service 在线校验，一次性消费）；</li>
     *   <li>user-service 幂等建档（新建分支发建档奖励；昵称落 user.nickname）；</li>
     *   <li>本地 {@code user_auth} 写入凭证行（PHONE + 三字段 + credential=密码哈希），
     *       已有凭证的标识按"已注册"拒绝（防账号接管）；</li>
     *   <li>签发登录令牌。</li>
     * </ol>
     *
     * @param command 注册命令
     * @return 登录结果
     */
    public LoginDTO register(RegisterCommand command) {
        String phone = command.phone();

        // 1. 验证码校验（本地边界：注册与密码登录共用同一凭证所有权方）
        verificationCodeService.verifyAndConsume(VerificationChannelEnum.SMS, phone, command.code());

        // 2. user-service 幂等建档（新用户发建档奖励；昵称落 user.nickname；已存在不覆盖）
        userAccountGateway.getOrCreateUserId(phone, IdentityTypeEnum.PHONE, command.username());

        // 3. 本地凭证建档（PHONE + 三字段 + credential=密码哈希）—— 修复"注册只写 user.password"的脑裂
        UserAuth userAuth = userAuthProvisioner.findByHashOrCreateWithCredential(
                IdentityTypeEnum.PHONE, BlindIndexUtil.hmac(phone), phone,
                passwordVerifier.encode(command.password()), null);

        // 4. 签发令牌（userId 取凭证行归属，保证 token 与凭证同源）
        Long userId = userAuth.getUserId();
        TokenPair tokenPair = loginTokenService.createLoginResponse(
                new TokenGenerateParam(userId, command.platform(), command.deviceId(), null, Map.of()));
        log.info("注册成功: userId={}, phone={}", userId, DesensitizeUtil.mask(phone));
        return LoginDTO.of(userId, tokenPair);
    }

    /**
     * 邮箱验证码签发用例（R3 闭环的"发码"半边）。
     *
     * <p>本服务不生成验证码、不落 Redis：只做邮箱归一化 + 转发 verification-service，
     * 由后者完成生成、有效期、60 秒重发限流与投递（经 notification-service 发邮件）。
     *
     * @param command 发码命令（邮箱 + 场景）
     * @return 签发结果（有效期秒数）
     */
    public IssuedCodeDTO sendEmailCode(EmailCodeCommand command) {
        EmailAddress email = EmailAddress.of(command.email());
        IssuedCodeDTO issued = verificationCodeIssueService.issueCode(
                VerificationChannelEnum.EMAIL, email.value(), command.scene());
        log.info("[auth][email-code][done] email={}, scene={}, expireSeconds={}, clientIp={}",
                DesensitizeUtil.maskEmail(email.value()), command.scene().getCode(),
                issued.expireSeconds(), command.clientIp());
        return issued;
    }

    /**
     * 重置密码用例 —— 认证边界收敛后在本服务本地闭环（ADR-017）。
     *
     * <p>流程：短信验证码校验并消费 → 按手机号盲索引定位 {@code user_auth} 凭证
     * → 写入新密码哈希。不再调用 user-service（user 表不再持有密码凭证）。
     *
     * <p>无本地事务：验证码消费与凭证更新各自原子（单条 UPDATE），失败由用户重试。
     *
     * @param command 重置密码命令
     * @throws BizException 手机号未注册（USER_NOT_FOUND）/ 验证码未通过
     */
    public void resetPassword(ResetPasswordCommand command) {
        String phone = command.phone();
        // 1. 校验并消费验证码（verification-service 在线校验，一次性消费）
        verificationCodeService.verifyAndConsume(VerificationChannelEnum.SMS, phone, command.code());

        // 2. 盲索引定位本地凭证（user_auth 是密码凭证的唯一载体）
        UserAuth userAuth = userAuthRepository
                .findByIdentityHash(IdentityTypeEnum.PHONE, BlindIndexUtil.hmac(phone))
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "该手机号未注册"));

        // 3. 写入新密码哈希
        userAuth.updatePassword(passwordVerifier.encode(command.newPassword()));
        userAuthRepository.updateByUserIdAndType(userAuth);
        log.info("密码重置成功: userId={}, phone={}", userAuth.getUserId(), DesensitizeUtil.mask(phone));
    }

    /**
     * 刷新令牌用例。
     *
     * @param refreshToken 刷新令牌
     * @return 新的 Token 对
     */
    public TokenPair refresh(String refreshToken) {
        return loginTokenService.createRefreshResponse(refreshToken);
    }

    /**
     * 会话检查用例（空闲超时判断）。
     *
     * @param authorizationHeader Authorization 请求头
     * @return 会话状态
     */
    public SessionCheckDTO checkSession(String authorizationHeader) {
        String accessToken = extractToken(authorizationHeader);
        if (accessToken == null) {
            return new SessionCheckDTO(false, 0L, IDLE_TIMEOUT_MINUTES);
        }
        return loginTokenService.checkSession(accessToken);
    }

    /**
     * 登出用例。
     *
     * @param refreshToken 刷新令牌
     */
    public void logout(String refreshToken) {
        loginTokenService.logout(refreshToken);
    }

    /**
     * 校验人机验证凭证（R3 闭环第 1 步）。
     *
     * <p>四态语义：
     * <ol>
     *   <li>前端未携带验证参数 → 不校验（登录本身不强制，风控命中时才会通过
     *       {@code CAPTCHA_REQUIRED} 要求前端重试并带回参数）</li>
     *   <li>本环境未启用验证（{@code captcha.enabled=false}）→ 跳过并 WARN，
     *       <b>不误拦截</b>（dev 未接入极验时前端拿不到可用凭证）</li>
     *   <li>参数残缺 → 参数错误（不得当作"未携带"而放行）</li>
     *   <li>校验不通过 → 验证码错误</li>
     * </ol>
     *
     * @param command 登录命令
     * @return true = 本次请求已通过人机验证（供风控处置判断是否仍需强制验证）
     * @throws BizException 参数残缺或人机验证未通过
     */
    private boolean verifyCaptchaIfProvided(LoginCommand command) {
        CaptchaVerifyParam captcha = command.captchaVerify();
        if (captcha == null || !captcha.isProvided()) {
            return false;
        }
        if (!captchaService.isEnabled()) {
            log.warn("[login][captcha][skip] 人机验证未启用，本次跳过校验 provider={}",
                    captcha.provider());
            return false;
        }
        if (!captcha.isComplete()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "人机验证参数不完整");
        }
        if (!captchaService.verify(captcha)) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证未通过");
        }
        return true;
    }

    private TokenGenerateParam buildTokenParam(Long userId, LoginCommand command) {
        return new TokenGenerateParam(userId, command.platform(), command.deviceId(),
                command.clientIp(), Map.of());
    }

    /** 从 Authorization header 提取 Token */
    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}
