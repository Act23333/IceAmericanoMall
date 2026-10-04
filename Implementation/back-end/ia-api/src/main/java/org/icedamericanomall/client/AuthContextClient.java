package org.icedamericanomall.client;

import org.icedamericanomall.dto.AuthContextCO;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.fallback.AuthContextClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 授权上下文 Feign Client —— 下游服务 → authorization-service（ADR-018）。
 *
 * <p>RBAC 五表与角色/权限聚合归属 authorization-service；下游服务（如 user-service
 * 的 /info 聚合）经本契约读取角色与权限，不再各自直读 sys_* 表。
 *
 * <p>{@link #revokeUserTokens(TokenRevokeCO)} 为 ADR-031 新增的用户级令牌吊销入口
 * （管理端禁用后立即下线），降级语义为 <b>fail-open</b>（见降级工厂）。
 */
@FeignClient(name = "authorization-service", path = "/internal/auth", contextId = "authContext",
        fallbackFactory = AuthContextClientFallback.class)
public interface AuthContextClient {

    /**
     * 用户角色 + 权限（授权域聚合；远端同时回填网关权限缓存 user:perms:{userId}）。
     */
    @GetMapping("/context/{userId}")
    AuthContextCO context(@PathVariable("userId") Long userId);

    /**
     * 批量角色编码（管理后台列表派生 roleType，避免逐行 N+1）。
     */
    @GetMapping("/roles")
    Map<Long, Set<String>> rolesByUserIds(@RequestParam("userIds") Collection<Long> userIds);

    /**
     * 设置用户角色（覆盖式；远端变更后失效该用户权限缓存）。
     */
    @PutMapping("/user/{id}/role")
    void setUserRole(@PathVariable("id") Long id, @RequestParam("roleType") Integer roleType);

    /**
     * 用户级令牌吊销：推进 {@code auth:tokenver:{userId}} 版本 + 拆除该用户全部 refresh 会话（ADR-031）。
     *
     * <p>调用方为管理端「禁用/冻结用户」成功路径；降级 fail-open（不阻断禁用主流程，但告警）。
     *
     * @param co 目标用户（{@code userId}）
     */
    @PostMapping("/token-version/revoke")
    void revokeUserTokens(@RequestBody TokenRevokeCO co);
}
