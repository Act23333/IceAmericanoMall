package org.icedAmericanoMall.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class SmsLoginReqDTO {
    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    @NotBlank private String phone;
    @Pattern(regexp = "^(\\d{6})$",
            message = "验证码错误")
    @NotBlank private String code;

}
