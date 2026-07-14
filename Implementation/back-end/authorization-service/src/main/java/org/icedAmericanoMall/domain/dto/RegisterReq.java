package org.icedAmericanoMall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @ClassName: RegisterDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/25 23:10
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.domain.dto
 */
@Data
public class RegisterReq {

    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,30}$", message = "用户名长度必须在1-30位之间，仅支持中文、字母、数字、下划线")
    private String username;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9a-zA-Z!@#$%^&*]{6,20}$",
            message = "密码必须为6-20位字母和数字组合（可含!@#$%^&*）")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "设备id不能为空")
    private String deviceId;

//     可选：幂等令牌（用于防止重复提交，可使用状态机处理幂等）
//    private String requestId;
}
