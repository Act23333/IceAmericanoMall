package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 重置密码请求（公开端点，短信验证码 + 新密码）。
 */
@Data
public class ResetPasswordReq {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9a-zA-Z]{6,20}$",
            message = "密码必须为6-20位字母和数字组合")
    private String newPassword;
}
