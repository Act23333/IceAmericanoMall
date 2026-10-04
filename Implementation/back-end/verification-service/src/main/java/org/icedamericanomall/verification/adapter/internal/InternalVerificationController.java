package org.icedamericanomall.verification.adapter.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.dto.VerificationCodeVerifyReqCO;
import org.icedamericanomall.dto.VerificationCodeVerifyRespCO;
import org.icedamericanomall.dto.VerificationResultCode;
import org.icedamericanomall.verification.app.VerificationManager;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.command.CodeIssueCommand;
import org.icedamericanomall.verification.app.command.CodeVerifyCommand;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校验服务内部端点（适配层）—— 仅集群内 Feign 调用，Gateway 拦截外网访问。
 *
 * <p>端点清单（与 ia-api {@code VerificationClient} 严格一一对应）：
 * <pre>
 * POST /internal/verification/code/issue      签发验证码（SMS / EMAIL）
 * POST /internal/verification/code/verify     校验并消费验证码
 * POST /internal/verification/captcha/verify  人机验证（GEETEST）
 * </pre>
 *
 * <p>本层只做 Request → Command 组装、领域结果 → 契约 DTO 映射、Result 之外的裸返回
 * （{@code /internal/**} 不包装 {@code Result}）；业务规则在领域层，Feign 调用在基础设施层。
 */
@Slf4j
@RestController
@RequestMapping("/internal/verification")
@RequiredArgsConstructor
public class InternalVerificationController {

    private final VerificationManager verificationManager;

    /** 签发验证码。 */
    @PostMapping("/code/issue")
    public VerificationCodeIssueRespCO issueCode(@Valid @RequestBody VerificationCodeIssueReqCO request) {
        VerificationCodeIssuer.IssuedCode result = verificationManager.issueCode(new CodeIssueCommand(
                request.getChannel(), request.getTarget(), request.getScene(), request.getRequestId()));
        return result.isSuccess()
                ? VerificationCodeIssueRespCO.ok(result.expireSeconds())
                : VerificationCodeIssueRespCO.fail(toResultCode(result.outcome()));
    }

    /** 校验并消费验证码。 */
    @PostMapping("/code/verify")
    public VerificationCodeVerifyRespCO verifyCode(@Valid @RequestBody VerificationCodeVerifyReqCO request) {
        VerificationOutcome outcome = verificationManager.verifyCode(new CodeVerifyCommand(
                request.getChannel(), request.getTarget(), request.getCode()));
        return outcome.isSuccess()
                ? VerificationCodeVerifyRespCO.ok()
                : VerificationCodeVerifyRespCO.fail(toResultCode(outcome));
    }

    /** 人机验证。 */
    @PostMapping("/captcha/verify")
    public CaptchaVerifyRespCO verifyCaptcha(@Valid @RequestBody CaptchaVerifyReqCO request) {
        boolean passed = verificationManager.verifyCaptcha(new CaptchaVerifyCommand(
                request.getProvider(), request.getParams(), request.getClientIp()));
        return passed
                ? CaptchaVerifyRespCO.ok()
                : CaptchaVerifyRespCO.fail(VerificationResultCode.CAPTCHA_FAILED);
    }

    /**
     * 领域结果 → 契约结果码。
     *
     * <p>使用 switch 表达式（穷尽枚举），新增 {@link VerificationOutcome} 取值时编译期即报错，
     * 避免出现「未映射的结果码被静默当成成功」。
     */
    private String toResultCode(VerificationOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> VerificationResultCode.SUCCESS;
            case CODE_MISMATCH -> VerificationResultCode.CODE_MISMATCH;
            case CODE_EXPIRED -> VerificationResultCode.CODE_EXPIRED;
            case TOO_MANY_ATTEMPTS -> VerificationResultCode.TOO_MANY_ATTEMPTS;
            case RATE_LIMITED -> VerificationResultCode.RATE_LIMITED;
            case SEND_FAILED -> VerificationResultCode.SEND_FAILED;
            case PARAM_INVALID -> VerificationResultCode.PARAM_INVALID;
        };
    }
}
