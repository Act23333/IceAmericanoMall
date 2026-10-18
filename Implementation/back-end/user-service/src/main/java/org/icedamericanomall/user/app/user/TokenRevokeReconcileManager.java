package org.icedamericanomall.user.app.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AuthContextClient;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.dto.TokenRevokeCO;
import org.icedamericanomall.exception.UpstreamException;
import org.icedamericanomall.user.app.oplog.OperationAuditConstants;
import org.icedamericanomall.user.app.oplog.OperationLogManager;
import org.icedamericanomall.user.domain.enums.UserStatusEnum;
import org.icedamericanomall.user.domain.model.oplog.OperationLog;
import org.icedamericanomall.user.domain.model.user.User;
import org.icedamericanomall.user.domain.repository.OperationLogRepository;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <b>令牌吊销的对账补偿</b>（U17 第二步，2026-10-18）—— 把「禁用已提交但吊销失败」收敛掉。
 *
 * <h2>为什么需要它</h2>
 * {@code UserAdminManager#updateStatus} 先提交本地状态、再调 authorization-service 吊销令牌
 * （{@code AuthContextClient#revokeUserTokens}）。吊销失败时<b>不能</b>让一次已生效的禁用对外报 5xx，
 * 所以调用点接住了异常 —— 若到此为止，就是「静默从 fallback 挪到了 catch」：
 * 账号已被禁用、旧令牌仍可登录，且没有任何东西会再来一次。本类就是那个「再来一次」。
 *
 * <h2>候选集与终结标记（只按列判定，禁止解析 {@code detail}）</h2>
 * <ul>
 *   <li><b>候选</b>：{@code action = USER:DISABLE_USER} 且 {@code create_time} 在时间窗内、{@code target} 非空；</li>
 *   <li><b>终结</b>：同 {@code target} 已存在 {@code action = USER:REVOKE_USER_TOKENS}
 *       且 {@code create_time >= 这条禁用记录}；</li>
 *   <li>候选有、终结无 ⇒ 重放。</li>
 * </ul>
 * 三种终结理由共用同一个 action，理由只写在 {@code detail} 里供人读 —— <b>不参与任何判定</b>
 * （挡的是「照着人读文案写条件」这类必然漂移的写法）。
 *
 * <h2>两个不写就会出真 bug 的条件（先写在这里，不是等测试红了再补）</h2>
 * <ol>
 *   <li><b>4xx 决不进对账</b>：{@code CLIENT}（下游明确拒绝，如目标非法/不存在）是<b>确定性失败</b>，
 *       重放必然再次失败 —— 若无条件重放，对账会<b>每轮重复同一次必然失败的调用</b>（死循环 + 日志洪泛）。
 *       故调用点与本类都对 4xx 直接写 {@code permanent-4xx} 终结标记。</li>
 *   <li><b>已重新启用必须跳过</b>：禁用 → 吊销失败 → 管理员又启用该用户（或用户已不存在）——
 *       此时再重放吊销会<b>把正常用户的登录态踢掉</b>。故重放前<b>重新读当前状态</b>，
 *       非 {@link UserStatusEnum#FROZEN} 一律写 {@code skipped: re-enabled or missing} 终结并跳过。</li>
 * </ol>
 *
 * <h2>时间窗（无状态重试的天然上限）</h2>
 * 只重放 {@link #LOOKBACK_HOURS} 小时内的禁用：超出窗口不再重试，转人工（见 {@code open-items} U17）。
 * <b>刻意不引入"重试次数"状态表</b> —— 那会立刻需要一个与 {@code operation_log} 并行的状态机，
 * 而补偿的全部状态其实已经在这张台账里了（有终结记录 / 没有）。
 *
 * <h2>并发与幂等</h2>
 * 本类<b>不加分布式锁</b>（沿用同仓 {@code RefundResumeJob} / {@code ReconciliationJob} 的既有形态）：
 * 多实例同时跑最多让同一用户被重放两次，而 {@code revokeUserTokens} 是<b>单调推进令牌版本号</b>，
 * 重复调用安全；残余风险是「同一轮多打一次 RPC」，不是数据错误。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevokeReconcileManager {

    /** 时间窗：只重放窗口内的禁用；超出即转人工（无状态重试的上限）。 */
    static final int LOOKBACK_HOURS = 24;

    /** 单轮上限：避免一次拉全表（窗口内正常也只有极少数条）。 */
    static final int BATCH_LIMIT = 100;

    /** 终结理由（仅人读）：成功。 */
    private static final String REVOKE_OK = "ok";

    /** 终结理由（仅人读）：4xx 确定性失败，重试必然重复失败。 */
    private static final String REVOKE_PERMANENT = "permanent-4xx";

    /** 终结理由（仅人读）：对账时发现已重新启用或用户已不存在 —— 不再重放。 */
    private static final String REVOKE_SKIPPED = "skipped: re-enabled or missing";

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;
    private final AuthContextClient authContextClient;
    private final OperationLogManager operationLogManager;

    /**
     * 跑一轮对账：找出「已禁用但吊销未终结」的用户并重放吊销。
     *
     * <p><b>不得 {@code @Transactional}</b>：方法内含 Feign 调用（硬约束）。
     *
     * @return 本轮真正重放并成功的条数（供 Job 日志）
     */
    public int reconcile() {
        LocalDateTime since = LocalDateTime.now().minusHours(LOOKBACK_HOURS);
        List<OperationLog> disables = operationLogRepository.findByActionSince(
                OperationAuditConstants.actionKey(OperationAuditConstants.MODULE_USER,
                        OperationAuditConstants.ACTION_DISABLE_USER),
                since, BATCH_LIMIT);
        if (disables.isEmpty()) {
            return 0;
        }
        String revokeAction = OperationAuditConstants.actionKey(OperationAuditConstants.MODULE_USER,
                OperationAuditConstants.ACTION_REVOKE_USER_TOKENS);

        int replayed = 0;
        for (OperationLog disable : disables) {
            String target = disable.getTarget();
            Long userId = parseUserId(target);
            if (userId == null) {
                // target 是纯业务键（"7"）；出现非数字说明写入侧被改坏了 —— 不猜、不跳过，打 ERROR 让人看见
                log.error("[token-revoke][reconcile][skip] target 非纯业务键，无法对账（检查写入侧）: target={}", target);
                continue;
            }
            if (operationLogRepository.existsByActionAndTargetSince(revokeAction, target, disable.getCreateTime())) {
                continue;
            }
            // 条件②：重放前重新读当前状态 —— 已重新启用/已删除的用户绝不能踢
            if (!stillFrozen(userId)) {
                recordOutcome(userId, REVOKE_SKIPPED);
                continue;
            }
            try {
                authContextClient.revokeUserTokens(TokenRevokeCO.of(userId));
                recordOutcome(userId, REVOKE_OK);
                replayed++;
                log.info("[token-revoke][reconcile] 已重放吊销成功: userId={}", userId);
            } catch (Exception e) {
                if (isDeterministicFailure(e)) {
                    // 条件①：4xx 确定性失败 ⇒ 终结，永不重放（否则每轮都重复同一次必然失败的调用）
                    recordOutcome(userId, REVOKE_PERMANENT);
                    log.error("[token-revoke][reconcile] 4xx 确定性失败，登记终结不再重试: userId={}", userId, e);
                } else {
                    log.warn("[token-revoke][reconcile] 仍不可用，留待下一轮（窗口 {} 小时内）: userId={}",
                            LOOKBACK_HOURS, userId, e);
                }
            }
        }
        return replayed;
    }

    /** 当前是否仍为禁用（对账的唯一事实来源，避免「凭台账猜状态」）。 */
    private boolean stillFrozen(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        return user.isPresent() && user.get().getStatus() == UserStatusEnum.FROZEN;
    }

    /** 写终结标记；写失败只打 ERROR —— 下一轮会再试，不影响正确性。 */
    private void recordOutcome(Long userId, String outcome) {
        try {
            operationLogManager.record(UserContext.getUserId(), OperationAuditConstants.MODULE_USER,
                    OperationAuditConstants.ACTION_REVOKE_USER_TOKENS, String.valueOf(userId), outcome);
        } catch (Exception e) {
            log.error("[token-revoke][reconcile] 终结标记写入失败（下一轮会再试）: userId={}, outcome={}",
                    userId, outcome, e);
        }
    }

    /** 4xx（CLIENT）属确定性失败：重试必然重复失败，不能进对账。 */
    private static boolean isDeterministicFailure(Throwable e) {
        UpstreamException upstream = UpstreamException.findIn(e);
        return upstream != null && upstream.getCategory() == ErrorCategory.CLIENT;
    }

    /** {@code target} 是纯业务键字符串；解析失败返回 {@code null}（不抛，交给调用侧打 ERROR）。 */
    private static Long parseUserId(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(target.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
