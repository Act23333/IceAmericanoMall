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
import org.icedamericanomall.common.utils.crypto.DesensitizeUtil;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
     * 签发验证码（生成 → 存储 → 投递）。
     *
     * @param command 签发命令
     * @return 领域签发结果（适配层负责映射为 ia-api 结果 DTO）
     */
    public VerificationCodeIssuer.IssuedCode issueCode(CodeIssueCommand command) {
        Optional<VerificationChannel> channel = VerificationChannel.find(command.channel());
        if (channel.isEmpty() || command.target() == null || command.target().isBlank()) {
            log.warn("[verify][issue][reject] 入参非法 channel={}", command.channel());
            return VerificationCodeIssuer.IssuedCode.fail(VerificationOutcome.PARAM_INVALID);
        }

        VerificationCodeIssuer.IssuedCode result = verificationCodeIssuer.issue(
                channel.get(), command.target().trim(), command.scene(), command.requestId());
        log.info("[verify][issue][done] channel={}, scene={}, requestId={}, target={}, outcome={}",
                channel.get().getCode(), command.scene(), command.requestId(),
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
        if (channel.isEmpty() || command.target() == null || command.target().isBlank()) {
            log.warn("[verify][verify][reject] 入参非法 channel={}", command.channel());
            return VerificationOutcome.PARAM_INVALID;
        }

        VerificationOutcome outcome = verificationCodeVerifier.verify(
                channel.get(), command.target().trim(), command.code());
        log.info("[verify][verify][done] channel={}, target={}, outcome={}",
                channel.get().getCode(), DesensitizeUtil.mask(command.target()), outcome);
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
