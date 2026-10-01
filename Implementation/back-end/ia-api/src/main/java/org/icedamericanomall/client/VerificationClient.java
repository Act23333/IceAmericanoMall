package org.icedamericanomall.client;

import org.icedamericanomall.dto.CaptchaVerifyReqCO;
import org.icedamericanomall.dto.CaptchaVerifyRespCO;
import org.icedamericanomall.dto.VerificationCodeIssueReqCO;
import org.icedamericanomall.dto.VerificationCodeIssueRespCO;
import org.icedamericanomall.dto.VerificationCodeVerifyReqCO;
import org.icedamericanomall.dto.VerificationCodeVerifyRespCO;
import org.icedamericanomall.fallback.VerificationClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 校验服务 Feign 客户端 —— 各服务 → verification-service。
 *
 * <p>verification-service 是本项目「校验能力」的唯一出口：
 * 验证码签发/校验（短信、邮件）+ 人机验证（极验 Geetest）。
 * 上游服务不再各自实现验证码生成与 Redis 存储。
 *
 * <p><b>返回值约定</b>：三个端点都返回结构化结果（{@code success + reason}），
 * 不使用异常表达业务失败 —— 因为 {@code /internal/**} 的业务异常会被
 * {@code GlobalExceptionHandler} 直接 rethrow，Feign 侧只能看到 HTTP 500，无法区分失败原因。
 *
 * <p><b>安全约定</b>：调用方必须「{@code success=true} 才放行」；
 * 降级（{@link VerificationClientFallback}）一律返回 {@code success=false}，绝不默认放行。
 */
@FeignClient(name = "verification-service", path = "/internal/verification", contextId = "verification",
        fallbackFactory = VerificationClientFallback.class)
public interface VerificationClient {

    /**
     * 签发验证码：生成 6 位数字 → 写 Redis（TTL 5 分钟 + 60 秒重发限流）→ 经通信服务投递。
     *
     * @param request 通道（SMS/EMAIL）、目标、业务场景
     * @return 签发结果（含有效期秒数）；重发过于频繁返回 {@code reason=RATE_LIMITED}
     */
    @PostMapping("/code/issue")
    VerificationCodeIssueRespCO issueCode(@RequestBody VerificationCodeIssueReqCO request);

    /**
     * 校验并消费验证码（一次性；错误次数超限即作废）。
     *
     * @param request 通道、目标、用户提交的验证码
     * @return {@code success=true} 表示校验通过且验证码已消费
     */
    @PostMapping("/code/verify")
    VerificationCodeVerifyRespCO verifyCode(@RequestBody VerificationCodeVerifyReqCO request);

    /**
     * 人机验证（当前 provider=GEETEST）。
     *
     * @param request 极验二次校验参数（lotNumber / captchaOutput / passToken / genTime）
     * @return {@code success=true} 表示人机验证通过
     */
    @PostMapping("/captcha/verify")
    CaptchaVerifyRespCO verifyCaptcha(@RequestBody CaptchaVerifyReqCO request);
}
