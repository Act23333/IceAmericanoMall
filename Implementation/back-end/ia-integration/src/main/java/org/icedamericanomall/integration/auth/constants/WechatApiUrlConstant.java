package org.icedamericanomall.integration.auth.constants;

/**
 * @ClassName: WechatApiUrlConstant
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/10 01:11
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.integration.auth.constants
 */
public class WechatApiUrlConstant {
    /** code 换 access_token+openid 接口。 */
    public static String OAUTH_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    /** 拉取用户信息接口。 */
    public static String OAUTH_USER_INFO_URL = "https://api.weixin.qq.com/sns/userinfo";
    
    /** 微信小程序登录接口 */
    public static final String MINI_CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?grant_type=authorization_code";

    /** 获取access_token的接口 */
    public static final String MINI_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential";

    /** 获取手机号的接口 */
    public static final String MINI_PHONE_REQUEST_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=";
}
