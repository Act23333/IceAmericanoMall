package org.icedamericanomall.verification.domain.repository;

import org.icedamericanomall.verification.domain.enums.VerificationChannel;

import java.time.Duration;

/**
 * 验证码仓储（领域层定义的端口，基础设施层用 Redis 实现）。
 *
 * <p><b>为什么方法粒度这么细：</b>「比对通过即消费」「重发限流」这类语义必须是
 * 单条 Redis 命令 / 单次 Lua 脚本的原子操作（否则并发下会出现同一验证码被消费两次、
 * 或并发双发短信），因此端口把原子性要求下沉到实现；
 * 而「什么算错误、错几次作废」等业务规则仍留在领域服务
 * （{@link org.icedamericanomall.verification.domain.service.VerificationCodeVerifier}）。
 *
 * <p>Key 布局见 {@code infrastructure/repository/VerificationRedisKeys}（跨服务契约）。
 */
public interface VerificationCodeRepository {

    /**
     * 保存验证码。
     *
     * @param channel 通道
     * @param target  目标
     * @param code    验证码明文
     * @param ttl     有效期
     */
    void save(VerificationChannel channel, String target, String code, Duration ttl);

    /**
     * 原子「比对并消费」：验证码与入参一致时立即删除并返回 true，否则不改动任何状态。
     *
     * <p>一次成功校验即消耗该验证码，杜绝重放（含并发重放）。
     *
     * @return true = 匹配且已消费
     */
    boolean consumeIfMatches(VerificationChannel channel, String target, String code);

    /** 验证码是否仍然存在（未过期、未被消费）。 */
    boolean exists(VerificationChannel channel, String target);

    /** 删除验证码（投递失败回滚 / 错误次数超限作废时使用）。 */
    void removeCode(VerificationChannel channel, String target);

    /**
     * 尝试获取发送配额：首次调用返回 true，并在窗口内阻止再次签发（原子 SETNX + TTL）。
     *
     * @return true = 允许发送
     */
    boolean tryAcquireSendQuota(VerificationChannel channel, String target, Duration window);

    /** 释放发送配额（投递失败时回滚，允许用户立即重试）。 */
    void releaseSendQuota(VerificationChannel channel, String target);

    /**
     * 累加错误次数（首次写入时设置窗口过期时间），返回累计值。
     *
     * @param window 计数窗口
     * @return 累计错误次数
     */
    int incrementFailures(VerificationChannel channel, String target, Duration window);

    /** 清空错误计数（校验通过或验证码作废后调用）。 */
    void clearFailures(VerificationChannel channel, String target);
}
