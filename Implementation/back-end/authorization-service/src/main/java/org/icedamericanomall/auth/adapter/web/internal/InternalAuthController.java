package org.icedamericanomall.auth.adapter.web.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.adapter.request.TokenRevokeReq;
import org.icedamericanomall.auth.app.AuthManager;
import org.icedamericanomall.auth.app.rbac.RoleManageManager;
import org.icedamericanomall.auth.app.rbac.SecurityContextManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 授权域内部端点（适配层）—— 供下游服务（user-service 等）Feign 调用。
 *
 * <p>端点清单（ADR-018：RBAC 归属授权域后新增）：
 * <pre>
 * GET  /internal/auth/context/{userId}      用户角色+权限（聚合并回填网关权限缓存）
 * GET  /internal/auth/roles?user_ids=       批量角色编码（管理后台列表派生 role_type）
 * PUT  /internal/auth/user/{id}/role        设置用户角色（覆盖式，变更后失效权限缓存）
 * POST /internal/auth/token-version/revoke  用户级令牌吊销（tokenVer INCR + 拆除全部 refresh 会话，ADR-031）
 * </pre>
 *
 * <p>{@code /internal/**} 不包装 {@code Result}；路径经 SecurityConfig 显式放行
 * （服务间调用，生产环境网络隔离）。
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final SecurityContextManager securityContextManager;
    private final RoleManageManager roleManageManager;
    private final AuthManager authManager;

    /** 用户角色 + 权限（聚合并回填网关权限缓存）。 */
    @GetMapping("/context/{userId}")
    public SecurityContextManager.AuthContext context(@PathVariable Long userId) {
        return securityContextManager.loadContext(userId);
    }

    /** 批量角色编码（管理后台列表派生 roleType）。 */
    @GetMapping("/roles")
    public Map<Long, Set<String>> rolesByUserIds(@RequestParam(name = "user_ids") Collection<Long> userIds) {
        return securityContextManager.rolesByUserIds(userIds);
    }

    /** 设置用户角色（覆盖式；变更后失效该用户权限缓存）。 */
    @PutMapping("/user/{id}/role")
    public void setUserRole(@PathVariable Long id, @RequestParam(name = "role_type") Integer roleType) {
        roleManageManager.setUserRole(id, roleType);
    }

    /**
     * 用户级令牌吊销：推进 {@code auth:tokenver:{userId}} 版本 + 拆除该用户全部 refresh 会话（ADR-031）。
     *
     * <p>由 user-service 在「管理端禁用/冻结用户」成功路径调用。方法无返回值 —— 内部端点不做
     * {@code Result} 包装（ADR-004），失败以原始 HTTP 异常透出；调用侧 Feign 降级为 fail-open
     * （禁用主流程不因吊销失败被阻断）。
     */
    @PostMapping("/token-version/revoke")
    public void revokeUserTokens(@RequestBody @Valid TokenRevokeReq req) {
        authManager.revokeUserTokens(req.getUserId());
    }
}
