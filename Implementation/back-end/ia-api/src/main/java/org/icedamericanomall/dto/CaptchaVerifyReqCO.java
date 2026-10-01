package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人机验证请求 DTO —— 各服务 → verification-service（Feign）。
 *
 * <p>字段与极验 v4「二次校验」接口一一对应（{@code lotNumber / captchaOutput / passToken / genTime}）；
 * {@code provider} 决定 verification-service 内部选择哪个人机验证厂商适配器（当前仅 GEETEST）。
 * captchaId 与 signToken 由服务端生成，不从前端传入。
 */
@Data
public class CaptchaVerifyReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 人机验证厂商：GEETEST（默认）；预留 RECAPTCHA 等 */
    private String provider = "GEETEST";

    /** 验证流水号 */
    @NotBlank(message = "验证流水号不能为空")
    private String lotNumber;

    /** 验证输出信息 */
    @NotBlank(message = "验证输出信息不能为空")
    private String captchaOutput;

    /** 验证通过标识 */
    @NotBlank(message = "验证通过标识不能为空")
    private String passToken;

    /** 验证通过时间戳（毫秒） */
    @NotBlank(message = "验证时间戳不能为空")
    private String genTime;

    /** 客户端 IP（风控归因，可为空） */
    private String clientIp;
}
