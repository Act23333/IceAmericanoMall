package org.icedamericanomall.verification.domain.service;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.gateway.NotificationGateway;
import org.icedamericanomall.verification.domain.model.VerificationCode;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.domain.port.CodeGenerator;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;

/**
 * 验证码签发领域服务（纯 POJO，由基础设施层 {@code DomainBeanConfig} 装配）。
 *
 * <p>业务规则：
 * <ol>
 *   <li><b>重发限流</b>：同一目标在 {@code sendLimitWindow}（默认 60 秒）内只能签发一次</li>
 *   <li><b>生成</b>：按策略位数生成数字验证码（随机算法由端口提供）</li>
 *   <li><b>存储</b>：写入仓储并设置 {@code codeTtl}（默认 5 分钟）</li>
 *   <li><b>投递</b>：经网关交给 notification-service；<b>投递失败即回滚</b>验证码与限流窗口，
 *       否则用户会拿到一个永远收不到、却又占着限流名额的验证码</li>
 * </ol>
 *
 * <p>注意：本服务只负责「签发 + 投递」，不负责校验（见 {@link VerificationCodeVerifier}）。
 */
public class VerificationCodeIssuer {

    private final VerificationCodeRepository repository;
    private final CodeGenerator codeGenerator;
    private final NotificationGateway notificationGateway;
    private final VerificationPolicy policy;

    public VerificationCodeIssuer(VerificationCodeRepository repository,
                                  CodeGenerator codeGenerator,
                                  NotificationGateway notificationGateway,
                                  VerificationPolicy policy) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.notificationGateway = notificationGateway;
        this.policy = policy;
    }

    /**
     * 签发并投递验证码。
     *
     * @param channel   通道（不可为 null）
     * @param target    目标（手机号 / 邮箱）
     * @param scene     业务场景
     * @param requestId 幂等 / 链路追踪标识
     * @return 签发结果（成功时携带有效期秒数）
     */
    public IssuedCode issue(VerificationChannel channel, String target, String scene, String requestId) {
        // 1. 重发限流（原子 SETNX）：窗口内重复请求直接拒绝
        if (!repository.tryAcquireSendQuota(channel, target, policy.sendLimitWindow())) {
            return IssuedCode.fail(VerificationOutcome.RATE_LIMITED);
        }

        // 2. 生成 + 3. 存储
        VerificationCode code = new VerificationCode(channel, target, codeGenerator.generate(policy.codeLength()));
        repository.save(channel, target, code.code(), policy.codeTtl());

        // 4. 投递（失败回滚验证码与限流，允许立即重试）
        try {
            notificationGateway.sendVerificationCode(code, requestId, scene);
        } catch (RuntimeException e) {
            repository.removeCode(channel, target);
            repository.releaseSendQuota(channel, target);
            return IssuedCode.fail(VerificationOutcome.SEND_FAILED);
        }

        return IssuedCode.ok((int) policy.codeTtl().toSeconds());
    }

    /**
     * 签发结果（领域内部返回类型）。
     *
     * @param outcome       结果
     * @param expireSeconds 有效期秒数（仅成功时有值）
     */
    public record IssuedCode(VerificationOutcome outcome, Integer expireSeconds) {

        public static IssuedCode ok(int expireSeconds) {
            return new IssuedCode(VerificationOutcome.SUCCESS, expireSeconds);
        }

        public static IssuedCode fail(VerificationOutcome outcome) {
            return new IssuedCode(outcome, null);
        }

        public boolean isSuccess() {
            return outcome != null && outcome.isSuccess();
        }
    }
}
