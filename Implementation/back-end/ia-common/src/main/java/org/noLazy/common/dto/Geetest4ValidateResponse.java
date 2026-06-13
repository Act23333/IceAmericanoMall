package org.noLazy.common.dto;

import lombok.Data;

@Data
public class Geetest4ValidateResponse {
    private String result;   // "success" 或 "fail"
    private String reason;   // 失败原因
   // private CaptchaArgs captchaArgs;  // 验证输出参数（可选）
    // 其他字段按需添加，如 captchaArgs
}