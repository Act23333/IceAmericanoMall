package org.icedamericanomall.integration.auth.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 微信用户信息 —— OAuth code 换取的结果（含 openid）。
 */
@Data
@Builder
public class WechatMiniLoginReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String phoneCode;
    private String nickname;
    private String avatarUrl;
}
