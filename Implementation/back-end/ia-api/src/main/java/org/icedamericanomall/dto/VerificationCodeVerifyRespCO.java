package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码校验结果 DTO —— verification-service → 调用方。
 *
 * <p>{@code reason} 取 {@link VerificationResultCode} 常量；
 * 调用方必须「仅当 {@code success=true} 才放行」，其余一律拒绝（安全优先）。
 */
@Data
public class VerificationCodeVerifyRespCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否校验通过（true = 已消费该验证码） */
    private boolean success;

    /** 结果码，取值见 {@link VerificationResultCode} */
    private String reason;

    /** 构造通过结果。 */
    public static VerificationCodeVerifyRespCO ok() {
        VerificationCodeVerifyRespCO resp = new VerificationCodeVerifyRespCO();
        resp.setSuccess(true);
        resp.setReason(VerificationResultCode.SUCCESS);
        return resp;
    }

    /** 构造未通过结果（reason 见 {@link VerificationResultCode}）。 */
    public static VerificationCodeVerifyRespCO fail(String reason) {
        VerificationCodeVerifyRespCO resp = new VerificationCodeVerifyRespCO();
        resp.setSuccess(false);
        resp.setReason(reason);
        return resp;
    }
}
