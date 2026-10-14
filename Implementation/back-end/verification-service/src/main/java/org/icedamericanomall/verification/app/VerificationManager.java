package org.icedamericanomall.verification.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.command.CodeIssueCommand;
import org.icedamericanomall.verification.app.command.CodeVerifyCommand;
import org.icedamericanomall.verification.app.port.CaptchaVerifyService;
import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.icedamericanomall.verification.domain.service.VerificationCodeVerifier;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 校验用例编排器（应用层）。
 *
 * <p>职责：解析通道 → 组装领域入参 → 调用领域服务 → 日志埋点。
 * 业务规则（限流、有效期、失败上限、一次性消费）全部在领域层，
 * 第三方调用（Feign / Geetest）在基础设施层，本类只做顺序编排。
 *
 * <p>事务：全部状态在 Redis，单条命令即原子操作，<b>不声明 {@code @Transactional}</b>；
 * 跨服务投递（Feign）失败由领域服务回滚 Redis 状态，不依赖数据库事务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationManager {

    /** 默认人机验证厂商 */
    private static final String DEFAULT_CAPTCHA_PROVIDER = "GEETEST";

    private final VerificationCodeIssuer verificationCodeIssuer;
    private final VerificationCodeVerifier verificationCodeVerifier;
    private final CaptchaVerifyService captchaVerifyService;

    /**
     * 签发验证码（可选人机验证前置）。
     *
     * <p>公开端点防轰炸策略：请求携带人机验证参数时先做二次校验
     * （未通过 fail-closed 抛 {@code CAPTCHA_ERROR}），未携带则依赖 IP 限流
     * 与按目标 60 秒重发限流兜底。内部服务调用（参数集为空）不受影响。
     *
     * @param command       签发命令
     * @param captchaVerify 人机验证命令（参数集为空 = 未携带，跳过）
     * @return 领域签发结果
     */
    /** 场景编码契约（ADR-051 修订）：大写字母开头 + 大写字母/数字/下划线，≤32。
     *  拒绝错大小写/特殊字符——防 Key 结构注入；前端错场景 fail-loud，而非静默落入新码池。 */
    private static final Pattern SCENE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,31}$");

    public VerificationCodeIssuer.IssuedCode issueCode(CodeIssueCommand command,
                                                       CaptchaVerifyCommand captchaVerify) {
        if (!captchaVerify.params().isEmpty() && !verifyCaptcha(captchaVerify)) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证未通过");
        }
        return issueCode(command);
    }

    /**
     * 签发验证码（生成 → 存储 → 投递）。
     *
     * @param command 签发命令
     * @return 领域签发结果（适配层负责映射为 ia-api 结果 DTO）
     */
    public VerificationCodeIssuer.IssuedCode issueCode(CodeIssueCommand command) {
        Optional<VerificationChannel> channel = VerificationChannel.find(command.channel());
        if (channel.isEmpty() || command.target() == null || command.target().isBlank()
                || command.scene() == null || command.scene().isBlank()) {
            log.warn("[verify][issue][reject] 入参非法 channel={}", command.channel());
            return VerificationCodeIssuer.IssuedCode.fail(VerificationOutcome.PARAM_INVALID);
        }

        // 场景编码契约（ADR-051 修订）：trim 后须匹配 [A-Z][A-Z0-9_]{0,31}
        String scene = command.scene().trim();
        if (!SCENE_PATTERN.matcher(scene).matches()) {
            log.warn("[verify][issue][reject] 场景编码非法 scene={}", command.scene());
            return VerificationCodeIssuer.IssuedCode.fail(VerificationOutcome.PARAM_INVALID);
        }
        VerificationCodeIssuer.IssuedCode result = verificationCodeIssuer.issue(
                channel.get(), command.target().trim(), scene, command.requestId());
        log.info("[verify][issue][done] channel={}, scene={}, requestId={}, target={}, outcome={}",
                channel.get().getCode(), scene, command.requestId(),
                DesensitizeUtil.mask(command.target()), result.outcome());
        return result;
    }

    /**
     * 校验并消费验证码。
     *
     * @param command 校验命令
     * @return 校验结果
     */
    public VerificationOutcome verifyCode(CodeVerifyCommand command) {
        Optional<VerificationChannel> channel = VerificationChannel.find(command.channel());
        if (channel.isEmpty() || command.target() == null || command.target().isBlank()
                || command.scene() == null || command.scene().isBlank()) {
            log.warn("[verify][verify][reject] 入参非法 channel={}", command.channel());
            return VerificationOutcome.PARAM_INVALID;
        }

        // 场景编码契约（ADR-051 修订）：与签发侧同一守卫（双端一致才不会错池）
        String scene = command.scene().trim();
        if (!SCENE_PATTERN.matcher(scene).matches()) {
            log.warn("[verify][verify][reject] 场景编码非法 scene={}", command.scene());
            return VerificationOutcome.PARAM_INVALID;
        }
        VerificationOutcome outcome = verificationCodeVerifier.verify(
                channel.get(), scene, command.target().trim(), command.code());
        log.info("[verify][verify][done] channel={}, scene={}, target={}, outcome={}",
                channel.get().getCode(), scene, DesensitizeUtil.mask(command.target()), outcome);
        return outcome;
    }

    /**
     * 人机验证（转发到第三方适配器）。
     *
     * @param command 人机验证命令
     * @return true = 验证通过
     */
    public boolean verifyCaptcha(CaptchaVerifyCommand command) {
        String provider = (command.provider() == null || command.provider().isBlank())
                ? DEFAULT_CAPTCHA_PROVIDER
                : command.provider().trim().toUpperCase();
        boolean passed = captchaVerifyService.verify(provider, command);
        log.info("[verify][captcha][done] provider={}, passed={}", provider, passed);
        return passed;
    }
}
