package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信 OAuth 登录请求 —— 前端授权回调拿到的 code。
 */
@Data
public class WechatWebLoginReqCO  {
    @NotBlank(message = "微信授权 code 不能为空")
    private String code;

    //state 是 OAuth2 授权码模式中的防 CSRF 攻击参数，由客户端生成、服务端校验的随机字符串。
    @NotBlank(message = "state 不能为空")
    private String state;   // 防 CSRF

}
