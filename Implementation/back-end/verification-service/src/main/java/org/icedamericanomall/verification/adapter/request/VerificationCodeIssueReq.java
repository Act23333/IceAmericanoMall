package org.icedamericanomall.verification.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 验证码签发请求（公开端点 {@code POST /api/verification/codes}，适配层入参）。
 *
 * <p><b>为何不复用 ia-api 的 {@code VerificationCodeIssueReqCO}</b>：
 * 那个类是<b>跨服务 Feign 契约</b>（供 authorization-service / user-service 经
 * {@code VerificationClient} 调 {@code /internal/verification/code/issue}），归 ia-api 所有；
 * 公开端点是<b>前端 HTTP 入参</b>，按适配层入参规范（{@code XxxReq}）在本层声明。
 * 两者 JSON 字段名与校验口径完全一致（channel / target / scene / requestId /
 * captchaProvider / captchaParams），对外报文契约不变。
 *
 * <p><b>防轰炸（可选人机验证前置）</b>：携带 {@code captchaProvider} + {@code captchaParams}
 * 时先做人机验证（失败 fail-closed），未携带则依赖 IP 限流与按目标重发限流兜底。
 */
@Data
public class VerificationCodeIssueReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通道：SMS / EMAIL（取值由服务端校验，非法值返回参数错误） */
    @NotBlank(message = "验证通道不能为空")
    private String channel;

    /** 目标：手机号（SMS）/ 邮箱（EMAIL） */
    @NotBlank(message = "发送目标不能为空")
    private String target;

    /** 业务场景：LOGIN / REGISTER / RESET_PASSWORD ... */
    @NotBlank(message = "业务场景不能为空")
    private String scene;

    /** 幂等 / 链路追踪标识（透传，可为空） */
    private String requestId;

    /** 人机验证厂商（可选，携带 captchaParams 时生效） */
    private String captchaProvider;

    /** 人机验证参数集（可选，厂商中立；key 由厂商 schema 决定） */
    private Map<String, String> captchaParams;
}
