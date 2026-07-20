package org.noLazy.common.domain;

import lombok.Builder;
import lombok.Getter;

import lombok.RequiredArgsConstructor;
import lombok.Singular;

import java.util.Set;

/**
 * @param roles       用于日志/前端，不参与权限判断
 * @param permissions 已聚合所有角色的权限 + 个人直接权限
 * @param traceId     全链路追踪 ID
 * @param tenantId    多租户标识
 * @ClassName: UserInfo
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/7/19 21:51
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.noLazy.common.domain
 */

@Builder
public record UserInfo(
        Long userId,
        String username,
        @Singular Set<String> roles,      // 支持 .role("ADMIN").role("USER")
        @Singular Set<String> permissions, // 支持 .permission("READ").permission("WRITE")
        String clientIp,
        String traceId,
        String tenantId
) {
    // 即使用了 @Singular，依然建议加上紧凑构造器做二次防御（防反射或手动new）
    public UserInfo {
        roles = (roles == null) ? Set.of() : Set.copyOf(roles);
        permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
    }
}
