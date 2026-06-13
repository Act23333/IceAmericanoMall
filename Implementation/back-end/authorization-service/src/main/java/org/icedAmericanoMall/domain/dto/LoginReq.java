package org.icedAmericanoMall.domain.dto;


import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.logging.log4j.util.Strings;
import org.icedAmericanoMall.enums.LoginTypeEnum;


/**
 * @ClassName: LoginFormDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/25 16:45
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.domain.dto
 */
@Data
public class LoginReq {

    @NotBlank
    private LoginTypeEnum loginType;
    @Size(min = 4, max = 20,  message = "用户名长度必须在4-20位之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
    message = "用户名只能以字母开头，支持字母、数字、下划线")
    private String username;

    // ========== 密码登录时使用的字段 ==========
    //@NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确") //^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[0-9a-zA-Z!@#$%^&*]{8,20}$
    private String phone;

    //@NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*])[0-9a-zA-Z!@#$%^&*]{8,20}$",
            message = "密码必须8-20位，包含大小写字母、数字、特殊符号")

    private String password;


    // ========== 短信登录时使用的字段 ==========
//    private String mobile;     // 手机号（短信登录必填）
    //@NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^(\\d{6})$",
            message = "验证码错误")
    private String code;

//    //人机验证码(后端收到后，调用验证服务（如腾讯验证码、极验）校验 captchaTicket 是否有效，而不是让用户直接输入静态验证码
//    private String captcha;
//
//    //幂等令牌（用于防止重复提交）
//    private String requestId;
//
//
//    //是否记住账号和密码，默认为false
//    private boolean rememberMe;
// 自定义校验：密码登录必须提供 username 或 phone 中的至少一个，且提供 password
//    @AssertTrue(message = "密码登录需要提供用户名或手机号，以及密码")
//    public boolean isValidForPassword() {
//        if (loginType == LoginTypeEnum.PASSWORD) {
//            boolean hasAccount = Strings.isNotEmpty(username) ||
//                    Strings.isNotEmpty(phone);
//            boolean hasAllAccount = Strings.isEmpty(username) ||
//                    Strings.isEmpty(phone);
//            return hasAccount && hasAllAccount && Strings.isNotEmpty(password);
//        }
//        return true;
//    }

    // 自定义校验：短信登录必须提供手机号和验证码
//    @AssertTrue(message = "短信登录需要提供手机号和验证码")
//    public boolean isValidForSms() {
//        if (loginType == LoginTypeEnum.SMS) {
//            return phone != null && !phone.trim().isEmpty() && code != null && !code.trim().isEmpty();
//        }
//        return true;
//    }

    // 🔥 统一自定义校验：根据登录类型校验
    @AssertTrue(message = "登录参数校验失败，请检查输入")
    public boolean isValidLoginParam() {
        // 1. 密码登录
        if (loginType == LoginTypeEnum.PASSWORD) {
            // 规则：用户名/手机号二选一 + 密码不能为空
            boolean hasAccount = Strings.isNotBlank(username) || Strings.isNotBlank(phone);
            boolean onlyOneAccount = !(Strings.isNotBlank(username) && Strings.isNotBlank(phone)); // 不同时传
            boolean hasPassword = Strings.isNotBlank(password);
            return hasAccount && onlyOneAccount && hasPassword;
        }

        // 2. 短信登录
        if (loginType == LoginTypeEnum.SMS) {
            // 规则：手机号 + 验证码 都不能为空
            return Strings.isNotBlank(phone) && Strings.isNotBlank(code);
        }

        // 3. 未知类型，校验失败
        return false;
    }

}
