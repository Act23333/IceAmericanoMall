package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码签发请求 DTO —— 各服务 → verification-service（Feign）。
 *
 * <p>签发语义：verification-service 生成验证码 → 写入 Redis（含重发限流）→
 * 经 notification-service 投递到目标通道。验证码本身不落库、不返回给调用方。
 */
@Data
public class VerificationCodeIssueReqCO implements Serializable {

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
}
