package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.domain.UserInfo;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.user.app.port.UserSecurityCache;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.repository.RoleRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 用户安全上下文装配（应用层）—— 角色/权限聚合 + 两级缓存写入。
 *
 * <p><b>跨服务契约（网关实时权限依赖，勿改）</b>：写 {@code user:info:{userId}}（JSON）与
 * {@code user:perms:{userId}}（SET，含 ROLE_ 角色），TTL 均 30 分钟；
 * 网关 {@code UserContextHeaderFilter} 读取后注入 X-User-Roles / X-User-Permissions。
 * 缓存读写在 {@link UserSecurityCache} 端口之后（Redis 实现）。
 */
@Component
@RequiredArgsConstructor
public class UserInfoManager {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSecurityCache userSecurityCache;

    /**
     * 加载用户安全上下文（缓存优先，未命中则查库聚合后回填）。
     *
     * @param userId 用户技术主键（跨服务口径）
     * @return 用户安全上下文（含角色 + 聚合权限）
     * @throws BizException 用户不存在
     */
    public UserInfo loadUserById(Long userId) {
        // 1. 先查缓存
        Optional<UserInfo> cached = userSecurityCache.findUserInfo(userId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 缓存未命中，查库聚合
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        Set<String> roles = roleRepository.findRoleCodesByUserId(userId);
        Set<String> perms = new HashSet<>();
        perms.addAll(roleRepository.findDirectPermissionCodesByUserId(userId));
        perms.addAll(roleRepository.findRolePermissionCodesByUserId(userId));

        UserInfo userInfo = UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .permissions(perms)
                .build();

        // 3. 写缓存：user:info JSON + user:perms SET（角色以 ROLE_ 前缀并入权限集合）
        Set<String> permissionSet = new HashSet<>(perms);
        permissionSet.addAll(roles);
        userSecurityCache.store(userInfo, permissionSet);

        return userInfo;
    }
}
