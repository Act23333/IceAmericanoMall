package org.icedamericanomall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人机验证结果 DTO —— verification-service → 调用方。
 *
 * <p>{@code reason} 取 {@link VerificationResultCode} 常量。
 */
@Data
public class CaptchaVerifyRespCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否通过人机验证 */
    private boolean success;

    /** 结果码，取值见 {@link VerificationResultCode} */
    private String reason;

    /** 构造通过结果。 */
    public static CaptchaVerifyRespCO ok() {
        CaptchaVerifyRespCO resp = new CaptchaVerifyRespCO();
        resp.setSuccess(true);
        resp.setReason(VerificationResultCode.SUCCESS);
        return resp;
    }

    /** 构造未通过结果（reason 见 {@link VerificationResultCode}）。 */
    public static CaptchaVerifyRespCO fail(String reason) {
        CaptchaVerifyRespCO resp = new CaptchaVerifyRespCO();
        resp.setSuccess(false);
        resp.setReason(reason);
        return resp;
    }
}
