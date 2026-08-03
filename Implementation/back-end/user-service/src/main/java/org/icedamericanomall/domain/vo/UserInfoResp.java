package org.icedamericanomall.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户信息响应 VO — V5.0 RBAC 增强
 * 对标京东/淘宝: 返回角色+权限供前端 UI 权限判断
 */
@Data
public class UserInfoResp {
    /** 业务唯一ID（安全！不暴露自增主键id） */
    private String userId;

    /** 用户名 */
    private String username;

    /** 手机号 */
    private String phone;

    /** 头像 */
    private String avatar;

    /** 用户状态 1-正常 0-禁用 */
    private Integer status;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 商城余额（单位：分） */
    private Integer balance;

    /** 角色类型: 0-普通用户, 1-商家, 2-管理员 (兼容旧字段) */
    private Integer roleType;

    /** RBAC 角色编码集合 (ROLE_USER/ROLE_SELLER/ROLE_ADMIN) */
    private Set<String> roles;

    /** RBAC 权限编码集合 (user:admin/seller:admin/...) */
    private Set<String> permissions;
}
