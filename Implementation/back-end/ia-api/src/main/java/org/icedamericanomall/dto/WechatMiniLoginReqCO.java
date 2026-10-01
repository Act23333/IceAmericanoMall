package org.icedamericanomall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @ClassName: WechatMiniLoginReqCO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/11 16:30
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.dto
 */
@Data
public class WechatMiniLoginReqCO {
    @NotBlank(message = "小程序登录 code 不能为空")
    private String code;

    /** 手机号授权凭证（可选，用户未授权手机号时为空） */
    private String phoneCode;

//    /** 微信昵称（可选） */
//    private String nickname;
//
//    /** 头像 URL（可选） */
//    private String avatarUrl;
}
