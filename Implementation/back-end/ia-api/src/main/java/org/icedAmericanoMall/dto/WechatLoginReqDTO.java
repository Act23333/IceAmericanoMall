package org.icedAmericanoMall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信 OAuth 登录请求 —— 前端授权回调拿到的 code。
 */
@Data
public class WechatLoginReqDTO {

    @NotBlank(message = "微信授权 code 不能为空")
    private String code;
}
