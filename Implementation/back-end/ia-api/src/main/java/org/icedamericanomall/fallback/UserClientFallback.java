package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.UserClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.exception.UpstreamFailures;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 纯用户域 Fallback（V2.5 拆分：auth/points/balance 已迁至各自 Client）—— <b>逐方法定类</b>。
 *
 * <p><b>{@code getOrCreateUser} —— ③ 命令（用户建档），fail-closed</b>：
 * <ul>
 *   <li>调用方 {@code auth.infrastructure.client.UserAccountGatewayImpl#getOrCreateUserId}
 *       直接把返回值透传给 {@code UserAuthProvisioner#createSensitive / #createPlain}，
 *       后者把它原样塞进 {@code UserAuth.userId} 再落 {@code user_auth}。
 *       {@code user_auth.user_id} 是 {@code NOT NULL} + 外键 {@code fk_user_auth_user}，
 *       且持久化层只把 {@code DuplicateKeyException} 翻译成领域异常
 *       （{@code UserAuthRepositoryImpl#insert}）—— 所以 {@code null} 不会被「妥善处理」，
 *       它会在插入时炸成一条<b>未经翻译的
 *       {@code DataIntegrityViolationException}</b>，被
 *       {@code GlobalExceptionHandler#handleException} 兜底成
 *       500「系统内部错误」，用户看到的是「服务炸了」而不是「稍后重试」。</li>
 *   <li>更糟的一条：{@code AuthManager#register} 第一步建档的返回值被<b>丢弃</b>
 *       （第 2 步「user-service 幂等建档」只取副作用）—— 若降级返回 {@code null}，
 *       注册流程会「看起来成功」地继续走到第 3 步再炸，或将「用户已存在但信息为空」
 *       误判为可继续建凭证，<b>这正是用户建档 fail-open 的形态</b>：拿不到 userId 就
 *       等于「没建过档」，任何后续逻辑都失去依据。</li>
 * </ul>
 * <p>因此本方法一律抛异常：{@code category == CLIENT}（下游 4xx + 已知 code，如
 * {@code PARAM_ERROR}）→ <b>原样抛 {@link UpstreamException}</b>（保留下游业务码/文案/traceId，
 * 见 {@code collaboration/09-constraints.md} §三·八·1）；{@code TRANSIENT} / {@code BUG} →
 * 抛 {@code BizException(SYSTEM_BUSY)}（503 / TRANSIENT / <b>可重试</b>），
 * <b>不得</b>用 {@code BUSINESS_EXECUTION_EXCEPTION}(3000 / 200 / RULE / 不可重试) ——
 * 那是「业务规则拒绝」的承载，会把可用性问题报成规则问题。
 *
 * <p><b>{@code countUsers} —— ① 展示型查询，fail-open</b>：唯一调用方
 * {@code trade.app.dashboard.AdminManager#dashboard} 已经写成
 * {@code count != null ? count : 0} 并用 {@code catch (Exception)} 兜底，
 * 语义是「管理端仪表盘的用户数指标」—— 少展示一个数字不影响任何业务判断或写操作，
 * 故降级返回 {@code 0L}，但<b>必须</b>在日志里写明这是<b>降级值</b>，
 * 否则「用户数为 0」会被当成真实业务事实（{@code 09-constraints} §三·八·2：
 * 只读展示类降级的文案不得谎报为成功）。
 *
 * <p>⚠️ 生效前提：调用方需开启 {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 且扫描 {@code org.icedamericanomall.fallback}，详见本包 {@code package-info}。
 */
@Slf4j
@Component
public class UserClientFallback implements FallbackFactory<UserClient> {

    /** 建档降级文案（fail-closed 路径）。 */
    private static final String PROVISION_DEGRADED_MESSAGE = "用户服务暂时无法处理，请稍后重试";

    @Override
    public UserClient create(Throwable cause) {
        // 类别判定走唯一入口（与 UpstreamErrorDecoder 同一判据）：异常类型 → 已类型化错误体 → 状态码。
        ErrorCategory category = UpstreamFailures.classify(cause);
        UpstreamException upstream = UpstreamException.findIn(cause);
        return new UserClient() {

            @Override
            public Long countUsers() {
                // ① 展示型：fail-open，但明确标注为降级值，禁止让 0 冒充真实用户数。
                log.warn("用户总数降级：本次返回降级值 0（非真实用户数，仅管理端仪表盘指标）"
                        + " category={}", category, cause);
                return 0L;
            }

            @Override
            public Long getOrCreateUser(UserAuthCO userAuthDTO) {
                // 仅记录标识类型：identifier 可能是手机号 / 邮箱（PII），禁止入日志。
                String identityType = userAuthDTO == null || userAuthDTO.getIdentityType() == null
                        ? null
                        : userAuthDTO.getIdentityType().name();
                if (category == ErrorCategory.CLIENT && upstream != null) {
                    log.warn("用户建档降级(透传): 下游业务失败 identityType={}, code={}, msg={}",
                            identityType, upstream.getCode(), upstream.getMsg());
                    throw upstream;
                }
                log.error("用户建档降级: 暂时无法处理（category={}；③ 命令 fail-closed，"
                        + "返回 null 会让调用方拿不到 userId 并触发 user_auth 非空约束违约）"
                        + " identityType={}", category, identityType, cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, PROVISION_DEGRADED_MESSAGE);
            }
        };
    }
}
