package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码校验请求 DTO —— 各服务 → verification-service（Feign）。
 *
 * <p>校验语义：一次性消费（成功即删除）；失败计数超过上限（5 次 / 30 分钟）时作废验证码。
 */
@Data
public class VerificationCodeVerifyReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通道：SMS / EMAIL */
    @NotBlank(message = "验证通道不能为空")
    private String channel;

    /** 目标：手机号（SMS）/ 邮箱（EMAIL） */
    @NotBlank(message = "校验目标不能为空")
    private String target;

    /** 用户提交的验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 业务场景（REGISTER/LOGIN/RESET_PASSWORD，ADR-051：场景参与 Key 隔离，必须与签发时一致） */
    @NotBlank(message = "验证场景不能为空")
    private String scene;
}
