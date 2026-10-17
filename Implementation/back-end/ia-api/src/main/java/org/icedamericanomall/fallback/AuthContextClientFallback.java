package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.AuthContextCO;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 授权上下文 Feign 降级工厂 —— <b>逐方法定类</b>（ADR-018 / ADR-031）。
 *
 * <p><b>分支纪律（§三·八·1）</b>：{@code category == CLIENT} 的 4xx 一律<b>原样抛
 * {@link UpstreamException}</b>（下游说「你参数/目标错」是业务事实）；只有
 * {@code TRANSIENT} / {@code BUG} 才走下面各方法的定类处置。
 *
 * <ul>
 *   <li>{@code context(userId)} —— <b>① 展示回填</b>。唯一调用方
 *       {@code user-service UserInfoManager#loadContext} 自带 try/catch，把结果并入
 *       {@code GET /api/user/info} 的角色/权限字段；拿不到时<b>不回填</b>（角色缺失即「无角色」，
 *       绝不默认放行）。降级返回空集合与调用方「空=不回填」的判据一致，决策不变，
 *       故允许 fail-open（§三·八·2 的「空=无」豁免情形）。</li>
 *   <li>{@code rolesByUserIds(ids)} —— <b>① 展示回填</b>。唯一调用方
 *       {@code UserAdminManager#pageUsers} 用它批量派生管理端列表的 {@code roleType}
 *       （{@code RoleTypeDerivation.fromRoleCodes(null)} → 留空），空 Map 只让该列留空。</li>
 *   <li>{@code setUserRole} —— <b>③ 命令</b>。授权变更静默丢失会让「已改角色」与 RBAC 五表
 *       不一致，必须 fail-closed（抛 {@code SYSTEM_BUSY}）。</li>
 *   <li>{@code revokeUserTokens} —— <b>best-effort（ADR-031）</b>。调用方
 *       {@code UserAdminManager#updateStatus} 在本地事务<b>提交之后</b>调用它，
 *       失败不得反向判定禁用失败；仅 WARN 告警后返回（吊销是登录态失效的加速器）。</li>
 * </ul>
 */
@Slf4j
@Component
public class AuthContextClientFallback implements FallbackFactory<AuthContextClient> {

    @Override
    public AuthContextClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        log.warn("[auth-context][fallback] authorization-service 不可用: category={}, cause={}",
                category, describe(cause));
        return new AuthContextClient() {

            @Override
            public AuthContextCO context(Long userId) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("[auth-context][fallback][透传] context 下游业务失败: userId={}, code={}, msg={}",
                            userId, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("[auth-context][fallback] context 降级为空角色/空权限（角色缺失=无权限，绝不默认放行）"
                        + " userId={}, category={}", userId, category);
                return AuthContextCO.builder()
                        .userId(userId)
                        .roles(Set.of())
                        .permissions(Set.of())
                        .build();
            }

            @Override
            public Map<Long, Set<String>> rolesByUserIds(Collection<Long> userIds) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("[auth-context][fallback][透传] rolesByUserIds 下游业务失败: size={}, code={}, msg={}",
                            userIds == null ? 0 : userIds.size(), upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.warn("[auth-context][fallback] 批量角色查询降级为空（管理端列表的角色列留空）"
                        + " size={}, category={}", userIds == null ? 0 : userIds.size(), category);
                return Map.of();
            }

            @Override
            public void setUserRole(Long id, Integer roleType) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("[auth-context][fallback][透传] setUserRole 下游业务失败: userId={}, roleType={}, code={}, msg={}",
                            id, roleType, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("[auth-context][fallback] 授权变更降级: 暂时无法写入（category={}；授权写不得静默丢失）"
                        + " userId={}, roleType={}", category, id, roleType, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "授权服务暂时无法写入，请稍后重试");
            }

            @Override
            public void revokeUserTokens(TokenRevokeCO co) {
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("[auth-context][fallback][透传] revokeUserTokens 下游业务失败: userId={}, code={}, msg={}",
                            co == null ? null : co.getUserId(), upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                // fail-open：禁用主流程不因吊销失败阻断，但必须告警（该用户令牌可能在认证域恢复前仍可用）
                log.warn("[auth-context][fallback] 用户级令牌吊销未生效，需人工跟进: userId={}, category={}",
                        co == null ? null : co.getUserId(), category);
            }
        };
    }

    /** 日志用异常摘要（不打印整条栈到 WARN 之外；cause 自身仍随 ERROR 分支输出）。 */
    private static String describe(Throwable cause) {
        return cause == null ? "unknown" : cause.toString();
    }
}
