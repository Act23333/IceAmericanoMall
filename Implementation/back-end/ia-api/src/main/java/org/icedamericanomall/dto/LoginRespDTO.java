package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ClassName: RegisterFormDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/1 22:30
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.dto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {
    private Long userId;
    private String username;
    /** 角色编码 (ROLE_USER/ROLE_SELLER/ROLE_ADMIN)，用于 JWT claims + 前端权限判断 */
    private String role;
    /** 角色列表 (RBAC 五表模型)，用于网关 header 传递 + 前端 UI 权限 */
    private java.util.Set<String> roles;
    /** 权限编码列表，用于 @PreAuthorize 方法级授权 */
    private java.util.Set<String> permissions;
}