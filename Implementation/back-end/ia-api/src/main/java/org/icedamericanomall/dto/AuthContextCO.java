package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 用户授权上下文 DTO —— 下游服务 → authorization-service（Feign，ADR-018）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthContextCO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户技术主键 */
    private Long userId;

    /** 角色编码集合（ROLE_ 前缀，如 ROLE_USER / ROLE_SELLER / ROLE_ADMIN） */
    private Set<String> roles;

    /** 权限编码集合（含直接授权 + 角色继承） */
    private Set<String> permissions;
}
