package org.icedamericanomall.integration.wechat;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信用户信息 —— OAuth code 换取的结果（含 openid）。
 */
@Data
public class WechatUserInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String openid;
    private String nickname;
    private String avatarUrl;
}
