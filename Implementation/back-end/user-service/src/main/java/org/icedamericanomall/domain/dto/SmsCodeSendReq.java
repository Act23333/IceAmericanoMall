package org.icedamericanomall.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @ClassName: SendCodeFormDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/26 15:58
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.domain.dto
 */
@Data
public class SmsCodeSendReq  {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "幂等校验码不能为空")
    private String requestId;

    @NotBlank(message = "人机验证凭证不能为空")
    private String captchaTicket;   // 人机验证服务返回的票据

    // 可选：验证服务可能需要的随机串
    private String captchaRandStr;


    // 极验 4.0 返回的字段
    @NotBlank
    private String lotNumber;

    @NotBlank
    private String captchaOutput;

    @NotBlank
    private String passToken;

    @NotBlank
    private String genTime;
}
