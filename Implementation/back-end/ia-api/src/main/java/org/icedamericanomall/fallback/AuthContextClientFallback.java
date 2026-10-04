package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AuthContextCO;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 授权上下文 Feign 降级工厂（fail-safe 语义，ADR-018）。
 *
 * <p>读操作（context/roles）降级返回空集合 —— 角色缺失时下游应表现为"无权限"，
 * 绝不默认放行；写操作（setUserRole）降级直接抛 SYSTEM_BUSY —— 授权变更不允许静默丢失。
 * 用户级令牌吊销（{@link AuthContextClient#revokeUserTokens}，ADR-031）降级为 <b>fail-open</b>：
 * 仅 WARN 告警后返回，不抛异常 —— 吊销是"登录态失效的加速器"，管理端禁用主流程
 * （已提交的本地状态变更）不得因认证域短暂不可用而被判失败。
 */
@Slf4j
@Component
public class AuthContextClientFallback implements FallbackFactory<AuthContextClient> {

    @Override
    public AuthContextClient create(Throwable cause) {
        log.warn("[auth-context][fallback] authorization-service 不可用: {}", cause.getMessage());
        return new AuthContextClient() {

            @Override
            public AuthContextCO context(Long userId) {
                return AuthContextCO.builder()
                        .userId(userId)
                        .roles(Set.of())
                        .permissions(Set.of())
                        .build();
            }

            @Override
            public Map<Long, Set<String>> rolesByUserIds(Collection<Long> userIds) {
                return Map.of();
            }

            @Override
            public void setUserRole(Long id, Integer roleType) {
                throw new BizException(ErrorCode.SYSTEM_BUSY, "授权服务暂不可用，请稍后重试");
            }

            @Override
            public void revokeUserTokens(TokenRevokeCO co) {
                // fail-open：禁用主流程不因吊销失败阻断，但必须告警（该用户令牌可能在认证域恢复前仍可用）
                log.warn("[auth-context][fallback] 用户级令牌吊销未生效，需人工跟进: userId={}",
                        co == null ? null : co.getUserId());
            }
        };
    }
}
