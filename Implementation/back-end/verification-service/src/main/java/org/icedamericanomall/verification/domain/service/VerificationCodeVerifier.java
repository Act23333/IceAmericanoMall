package org.icedamericanomall.verification.domain.service;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;
import org.icedamericanomall.verification.domain.enums.VerificationOutcome;
import org.icedamericanomall.verification.domain.model.VerificationPolicy;
import org.icedamericanomall.verification.domain.repository.VerificationCodeRepository;

/**
 * 验证码校验领域服务（纯 POJO）。
 *
 * <p>业务规则：
 * <ol>
 *   <li><b>一次性消费</b>：比对成功即删除（由仓储的原子「比对并消费」保证，防重放/并发重放）</li>
 *   <li><b>防爆破</b>：错误次数累计，达到 {@code maxFailures}（默认 5）时<b>作废验证码</b>并清空计数，
 *       攻击者无法用同一验证码无限次枚举</li>
 *   <li><b>过期即失败</b>：验证码不存在（未发送 / 已过期 / 已被消费）统一返回
 *       {@link VerificationOutcome#CODE_EXPIRED}，不区分原因以免泄露「该号码是否正在登录」</li>
 * </ol>
 */
public class VerificationCodeVerifier {

    private final VerificationCodeRepository repository;
    private final VerificationPolicy policy;

    public VerificationCodeVerifier(VerificationCodeRepository repository, VerificationPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    /**
     * 校验并消费验证码。
     *
     * @param channel 通道
     * @param target  目标
     * @param code    用户提交的验证码
     * @return 校验结果（{@link VerificationOutcome#SUCCESS} 表示通过且已消费）
     */
    public VerificationOutcome verify(VerificationChannel channel, String target, String code) {
        if (code == null || code.isBlank()) {
            return VerificationOutcome.PARAM_INVALID;
        }

        // 1. 原子比对并消费：成功即删除，杜绝重放
        if (repository.consumeIfMatches(channel, target, code)) {
            repository.clearFailures(channel, target);
            return VerificationOutcome.SUCCESS;
        }

        // 2. 不存在 → 未发送 / 已过期 / 已被消费（不细分，避免信息泄露）
        if (!repository.exists(channel, target)) {
            return VerificationOutcome.CODE_EXPIRED;
        }

        // 3. 存在但不匹配 → 计数，达到上限即作废验证码
        int failures = repository.incrementFailures(channel, target, policy.failureWindow());
        if (failures >= policy.maxFailures()) {
            repository.removeCode(channel, target);
            repository.clearFailures(channel, target);
            return VerificationOutcome.TOO_MANY_ATTEMPTS;
        }
        return VerificationOutcome.CODE_MISMATCH;
    }
}
