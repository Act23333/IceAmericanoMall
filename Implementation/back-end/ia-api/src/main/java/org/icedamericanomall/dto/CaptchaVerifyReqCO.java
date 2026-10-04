package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 人机验证请求 DTO —— 各服务 → verification-service（Feign）。
 *
 * <p><b>厂商中立契约</b>：本 DTO 只携带 {@code provider} + 不透明参数集（{@code params}），
 * 不含任何厂商专属字段 —— 极验 v4 的 lotNumber/captchaOutput/passToken/genTime 只是
 * {@code params} 的约定 key，由 verification-service 按 provider 解释并路由到
 * ia-integration 的厂商适配器。新增厂商（如腾讯验证码 ticket/randstr）只扩展 provider
 * 取值，本契约与上游各层均不改动。captchaId 与签名由服务端生成，不从前端传入。
 */
@Data
public class CaptchaVerifyReqCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 人机验证厂商：GEETEST（默认）；预留 RECAPTCHA 等 */
    private String provider = "GEETEST";

    /** 厂商校验参数集（key 由厂商 schema 决定；GEETEST v4：lotNumber / captchaOutput / passToken / genTime） */
    private Map<String, String> params;

    /** 客户端 IP（风控归因，可为空） */
    private String clientIp;
}
