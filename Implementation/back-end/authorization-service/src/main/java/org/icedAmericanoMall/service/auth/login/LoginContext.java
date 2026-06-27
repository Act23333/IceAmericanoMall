package org.icedAmericanoMall.service.auth.login;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.auth.LoginReq;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginContext {

    private static final int MAX_FAIL_COUNT = 5;
    private static final String FAIL_KEY_PREFIX = "login_fail:";
    private static final String LOCK_KEY_PREFIX = "lock:login:";
    private static final String FAIL_TTL_SECONDS = "60";

    private final LoginStrategyFactory loginStrategyFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> checkLimitScript;
    private final RedisScript<Void> loginRateLimitRedisScript;
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
            stringRedisTemplate.delete(failKey);
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

    private void checkFailLimit(String failKey) {
        Long result = stringRedisTemplate.execute(
                checkLimitScript,
                Collections.singletonList(failKey),
                MAX_FAIL_COUNT
        );
        if (result != null && result == -1) {
            throw new BizException(ErrorCode.FREQUENT_ERROR, "登录失败次数过多，请1分钟后再试");
        }
    }

    private void recordLoginFail(String failKey) {
        stringRedisTemplate.execute(
                loginRateLimitRedisScript,
                Collections.singletonList(failKey),
                FAIL_TTL_SECONDS
        );
    }

    private void unlockLogin(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
