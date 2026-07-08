package org.icedAmericanoMall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录门面 — Redisson 分布式锁 + 失败计数限流。
 * 使用 Redisson 原生 API 避免 StringRedisTemplate 与 Redisson 编码冲突。
 */
@Service
@RequiredArgsConstructor
public class LoginContext {

    private static final int MAX_FAIL_COUNT = 5;
    private static final String FAIL_KEY_PREFIX = "login_fail:";
    private static final String LOCK_KEY_PREFIX = "lock:login:";
    private static final Duration FAIL_TTL = Duration.ofSeconds(60);

    private final LoginStrategyFactory loginStrategyFactory;
    private final RedissonClient redissonClient;

    public OAuth2TokenResp login(LoginReq request) {
        String account = request.getAccount();
        String failKey = FAIL_KEY_PREFIX + account;
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + account);
        try {
            lockLogin(lock);
            checkFailLimit(failKey);
            OAuth2TokenResp response = loginStrategyFactory
                    .getStrategy(request.getIdentityType(), request.getCredentialType())
                    .login(request);
            clearFailCount(failKey);
            return response;
        } catch (BizException e) {
            recordLoginFail(failKey);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统异常");
        } finally {
            unlockLogin(lock);
        }
    }

    private void lockLogin(RLock lock) throws InterruptedException {
        boolean lockSuccess = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (!lockSuccess) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "请求过于频繁，请稍后再试");
        }
    }

    /** 检查失败次数是否超限 — Redisson RAtomicLong */
    private void checkFailLimit(String failKey) {
        RAtomicLong counter = redissonClient.getAtomicLong(failKey);
        long current = counter.get();
        if (current >= MAX_FAIL_COUNT) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "登录失败次数过多，请1分钟后再试");
        }
    }

    /** 记录失败次数 — Redisson INCR + EXPIRE 原子操作 */
    private void recordLoginFail(String failKey) {
        RAtomicLong counter = redissonClient.getAtomicLong(failKey);
        counter.expireIfNotSet(FAIL_TTL);
        counter.incrementAndGet();
        counter.expire(FAIL_TTL);
    }

    /** 成功后清除失败计数 */
    private void clearFailCount(String failKey) {
        redissonClient.getAtomicLong(failKey).delete();
    }

    private void unlockLogin(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
