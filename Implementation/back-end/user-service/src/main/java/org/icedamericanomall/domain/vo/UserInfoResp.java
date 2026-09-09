package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @ClassName: UserVO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/20 12:23
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.domain.vo
 */
@Data
public class UserInfoResp {
    // 业务唯一ID（安全！不暴露自增主键id）
    private String userId;

    // 用户名
    private String username;

    // 手机号
    private String phone;

    // 头像
    private String avatar;

    // 用户状态 1-正常 0-禁用
    private Integer status;

    // 注册时间
    private LocalDateTime registerTime;

    // 商城余额（单位：分，业务需要才加）
    private Integer balance;
}
