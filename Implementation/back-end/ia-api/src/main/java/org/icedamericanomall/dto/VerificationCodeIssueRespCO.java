package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码签发结果 DTO —— verification-service → 调用方。
 *
 * <p>{@code reason} 取 {@link VerificationResultCode} 常量。
 * 验证码明文<b>不会</b>出现在响应里（只经通道投递）。
 */
@Data
public class VerificationCodeIssueRespCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否已受理并投递成功 */
    private boolean success;

    /** 结果码，取值见 {@link VerificationResultCode} */
    private String reason;

    /** 验证码有效期（秒），success=true 时有效 */
    private Integer expireSeconds;

    /** 构造成功结果。 */
    public static VerificationCodeIssueRespCO ok(int expireSeconds) {
        VerificationCodeIssueRespCO resp = new VerificationCodeIssueRespCO();
        resp.setSuccess(true);
        resp.setReason(VerificationResultCode.SUCCESS);
        resp.setExpireSeconds(expireSeconds);
        return resp;
    }

    /** 构造失败结果（reason 见 {@link VerificationResultCode}）。 */
    public static VerificationCodeIssueRespCO fail(String reason) {
        VerificationCodeIssueRespCO resp = new VerificationCodeIssueRespCO();
        resp.setSuccess(false);
        resp.setReason(reason);
        return resp;
    }
}
