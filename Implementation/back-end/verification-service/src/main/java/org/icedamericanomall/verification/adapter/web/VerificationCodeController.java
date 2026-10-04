package org.icedamericanomall.verification.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.annotation.RateLimit;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IpUtil;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.verification.adapter.request.VerificationCodeIssueReq;
import org.icedamericanomall.verification.app.VerificationManager;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.command.CodeIssueCommand;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.service.VerificationCodeIssuer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 验证码公开端点（适配层）—— 验证码签发能力的对外 HTTP 契约。
 *
 * <p><b>归属说明（边界重构，ADR-016 收尾）</b>：验证码签发/校验/人机验证的业务能力
 * （生成、有效期、重发限流、消费语义）都在本服务，此前由 authorization-service 的
 * {@code POST /api/auth/email/code} 代理暴露；重构后前端直连本端点：
 * <pre>
 * POST /api/verification/codes    签发验证码（SMS / EMAIL，body: VerificationCodeIssueReq）
 * </pre>
 * 登录侧的"校验并消费"仍由 authorization-service 在登录用例内经 Feign 调内部端点完成。
 *
 * <p>入参对象化（V5.4 结构统一）：公开端点用适配层 {@link VerificationCodeIssueReq}；
 * 内部端点继续用 ia-api 的 {@code VerificationCodeIssueReqCO}（跨服务 Feign 契约，见
 * {@code InternalVerificationController}），两者 JSON 字段名一致，对外报文契约不变。
 *
 * <p>与 {@code /internal/**} 不同，本层失败以 {@code BizException} 抛出，
 * 由 ia-common 全局异常处理器包装为 {@code Result}（异常作为唯一失败出口）。
 */
@Slf4j
@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationCodeController {

    private final VerificationManager verificationManager;

    /**
     * 签发验证码（IP 限流 + 可选人机验证前置；按目标 60 秒重发限流由本服务内建）。
     *
     * <p>承接原 authorization-service {@code /api/auth/email/code} 与 user-service
     * {@code /api/user/code} 的公开发码职责（ADR-016 收尾）。
     */
    @PostMapping("/codes")
    @RateLimit(key = "ip", limit = 10, duration = 1, unit = TimeUnit.MINUTES)
    public Result<VerificationCodeIssueRespCO> issueCode(@Valid @RequestBody VerificationCodeIssueReq request,
                                                         HttpServletRequest httpRequest) {
        VerificationCodeIssuer.IssuedCode result = verificationManager.issueCode(
                new CodeIssueCommand(request.getChannel(), request.getTarget(),
                        request.getScene(), request.getRequestId()),
                new CaptchaVerifyCommand(request.getCaptchaProvider(),
                        request.getCaptchaParams(), IpUtil.getIpAddr(httpRequest)));
        if (!result.isSuccess()) {
            throw toException(result.outcome());
        }
        return Result.ok(VerificationCodeIssueRespCO.ok(result.expireSeconds()));
    }

    /**
     * 领域结果 → 业务异常（公开端点：异常作为唯一失败出口）。
     *
     * <p>校验类结果（用户输入问题）按语义映射到 CAPTCHA_ERROR / CAPTCHA_EXPIRED /
     * FREQUENT_ERROR，不再一律折叠为 SYSTEM_BUSY「服务暂不可用」，
     * 便于前端区分提示；仅真正的基础设施故障才落到 SYSTEM_BUSY。
     */
    private BizException toException(VerificationOutcome outcome) {
        return switch (outcome) {
            case PARAM_INVALID -> new BizException(ErrorCode.PARAM_ERROR, "验证码请求参数不合法");
            case RATE_LIMITED -> new BizException(ErrorCode.FREQUENT_ERROR, "验证码发送过于频繁，请稍后重试");
            case SEND_FAILED -> new BizException(ErrorCode.SYSTEM_BUSY, "验证码投递失败，请稍后重试");
            case CODE_MISMATCH -> new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误");
            case CODE_EXPIRED -> new BizException(ErrorCode.CAPTCHA_EXPIRED, "验证码已过期");
            case TOO_MANY_ATTEMPTS -> new BizException(ErrorCode.FREQUENT_ERROR, "验证码错误次数过多，请稍后重试");
            default -> new BizException(ErrorCode.SYSTEM_BUSY, "验证码服务暂不可用，请稍后重试");
        };
    }
}
