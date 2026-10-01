package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * @ClassName: LoginRespCO
 * @Description: 认证域登录响应 —— user-service /internal/auth 端点与 AuthClient 的共享契约
 * @Author: noLazy
 * @Date: 2026/4/1 22:30
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedamericanomall.dto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespCO {
    private String userId;
    private String username;
    /* 角色列表 (RBAC 五表模型)，用于网关 header 传递 + 前端 UI 权限 */
    private Set<String> roles;
    /* 权限编码列表，用于 @PreAuthorize 方法级授权 */
    private Set<String> permissions;
    /* 用户访问服务使用的设备类型 */
    private String deviceType;
}
