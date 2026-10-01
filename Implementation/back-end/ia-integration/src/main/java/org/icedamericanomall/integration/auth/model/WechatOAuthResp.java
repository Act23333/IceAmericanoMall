package org.icedamericanomall.integration.auth.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信用户信息 —— OAuth code 换取的结果（含 openid）。
 */
@Data
public class WechatOAuthResp implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String openid;
    private String phone;
    private String nickname;
    private String avatarUrl;
}
