package org.icedAmericanoMall.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.logging.log4j.util.Strings;



/**
 * @ClassName: LoginFormDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/1 22:28
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.dto
 */
@Data
public class PasswordLoginReqDTO {
    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    private String phone;
    @Size(min = 4, max = 20,  message = "用户名长度必须在4-20位之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
            message = "用户名只能以字母开头，支持字母、数字、下划线")
    private String username;
    @NotBlank
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[0-9a-zA-Z!@#$%^&*]{8,20}$",
            message = "密码必须8-20位，包含大小写字母、数字、特殊符号")
    private String password;

    @AssertTrue(message = "密码登录的账号必须提供用户名或密码")
    public boolean isValid() {
        return (!Strings.isBlank(phone) || !Strings.isBlank(username));
    }
}
