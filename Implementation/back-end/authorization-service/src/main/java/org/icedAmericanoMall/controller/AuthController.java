package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.icedAmericanoMall.client.UserClient;
import org.icedAmericanoMall.domain.dto.LoginReq;
import org.icedAmericanoMall.domain.dto.OAuth2TokenResp;
import org.icedAmericanoMall.domain.dto.RefreshTokenInfo;
import org.icedAmericanoMall.domain.dto.RegisterReq;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.enums.LoginTypeEnum;
import org.icedAmericanoMall.utils.JwtUtils;
import org.icedAmericanoMall.utils.RefreshTokenUtils;
import org.jetbrains.annotations.NotNull;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.BeanUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserClient userClient;
    private final JwtUtils jwtService;
    private final RefreshTokenUtils refreshTokenUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> checkLimitScript;
    private final RedisScript<Void> loginRateLimitRedisScript;
    private final RedissonClient redissonClient;
    @Value("${jwt.access-token-ttl:3600}")
    private Long accessTokenTtl;

    @Value("${jwt.refresh-token-ttl:604800}")
    private Long refreshTokenTtl;

    /**
     * 统一登录入口 —— 根据 loginType 区分密码登录 / 验证码登录。
     *
     * <pre>
     * Scenario: 密码登录成功 → JWT 签发
     *   Given 手机号/用户名已注册且状态正常
     *   When POST /api/auth/login with {username/phone, password, loginType=PASSWORD}
     *   Then 通过 Feign 调用 user-service 校验密码
     *   And 生成 JWT accessToken（含 userId, username, role）
     *   And 生成 refreshToken 存入 Redis
     *   And 返回 accessToken + refreshToken
     *
     * Scenario: 验证码登录成功 → JWT 签发
     *   Given 手机号已注册（或首次登录自动注册）
     *   When POST /api/auth/login with {phone, code, loginType=SMS}
     *   Then 通过 Feign 调用 user-service 校验验证码
     *   And 返回 JWT Token
     *
     * Scenario: 登录失败次数过多 → 锁定1分钟
     *   Given 同一账号1分钟内登录失败5次
     *   When 第6次尝试登录
     *   Then 返回 "登录失败次数过多，请1分钟后再试"
     *
     * Scenario: 登录成功清除失败计数
     *   Given 之前有登录失败记录
     *   When 本次登录成功
     *   Then 清除 Redis 中的失败计数
     * </pre>
     */
    @PostMapping("/login")
    public Result<OAuth2TokenResp> login(@Validated @RequestBody LoginReq request) {
        String username = request.getUsername();
        String keyIdentify = Strings.isEmpty(username) ? request.getPhone() : username;

        //登录限流，如果登录在1分钟内失败5次，距上次失败时间间隔1分钟后重试
        String failKey = "login_fail:" + keyIdentify;
        String lockKey = "lock:login:" + keyIdentify;

        RLock lock = redissonClient.getLock(lockKey);
        try {

            // 尝试加锁：等待1秒，锁自动释放10秒（防死锁）
            boolean lockSuccess = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!lockSuccess) {
                throw new BizException(ErrorCode.FREQUENT_ERROR, "请求过于频繁，请稍后再试");
            }

            Long result = stringRedisTemplate.execute(
                    checkLimitScript,
                    Collections.singletonList(failKey),
                    5
            );

            if (result != null && result == -1) {
                throw new BizException(ErrorCode.FREQUENT_ERROR, "登录失败次数过多，请1分钟后再试");
            }
            LoginRespDTO userResp;
            if (request.getLoginType() == LoginTypeEnum.PASSWORD) {
                PasswordLoginReqDTO pwdReq = new PasswordLoginReqDTO();
                if (Strings.isEmpty(username)) {
                    pwdReq.setPhone(request.getPhone());
                } else
                    pwdReq.setUsername(request.getUsername());
                pwdReq.setPassword(request.getPassword());
                userResp = userClient.loginByPassword(pwdReq);
            } else {
                SmsLoginReqDTO smsReq = new SmsLoginReqDTO();
                smsReq.setPhone(request.getPhone());
                smsReq.setCode(request.getCode());
                userResp = userClient.loginBySms(smsReq);
            }
            //登录成功，清除失败计数
            stringRedisTemplate.delete(failKey);
            return Result.ok(createLoginResponse(userResp));
        } catch (BizException e) {
            //登录失败：【原子自增+过期】
            stringRedisTemplate.execute(
                    loginRateLimitRedisScript,
                    Collections.singletonList(failKey),
                    "60"
            );
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "系统异常");
        } finally {
            // 释放锁
            // 防御性编程，防止当前线程没有拿到锁或者手动解锁，锁超时，加锁失败等问题unlock抛出异常，
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * <pre>
     * Scenario: 注册成功 → 直接返回 Token
     *   Given 手机号未注册
     *   And 验证码有效
     *   When POST /api/auth/register
     *   Then 通过 Feign 调用 user-service 完成注册
     *   And 返回 JWT accessToken + refreshToken
     * </pre>
     */
    @PostMapping("/register")
    public Result<OAuth2TokenResp> register(@Validated @RequestBody RegisterReq request) {
        RegisterReqDTO registerReqDTO = BeanUtils.copyBean(request, RegisterReqDTO.class);
        LoginRespDTO userResp = userClient.register(registerReqDTO);
        return Result.ok(createLoginResponse(userResp));
    }

    @NotNull
    private OAuth2TokenResp createLoginResponse(LoginRespDTO userResp) {
        Long userId = userResp.getUserId();
        String username = userResp.getUsername();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", userResp.getRole() != null ? userResp.getRole() : "ROLE_USER");
        String accessToken = jwtService.generateToken(claims);
        String refreshToken = refreshTokenUtils.createRefreshToken(
                userId,
                username,
                refreshTokenTtl
        );

        return buildTokenResponse(accessToken, userId, username, refreshToken);
    }

    @NotNull
    private OAuth2TokenResp buildTokenResponse(String accessToken, Long userId, String username, String tokenId) {
        OAuth2TokenResp response = new OAuth2TokenResp();
        response.setAccessToken(accessToken);
        response.setRefreshToken(tokenId);
        response.setExpiresIn(accessTokenTtl);
        response.setUserId(userId);
        response.setUsername(username);
        return response;
    }

    /**
     * <pre>
     * Scenario: Token 刷新（令牌轮换）
     *   Given 持有有效的 refreshToken（Redis 中存在）
     *   When POST /api/auth/refresh?refresh_token=xxx
     *   Then 返回新的 accessToken 和 refreshToken
     *   And 旧的 refreshToken 被轮换（rotate）
     * </pre>
     */
    @PostMapping("/refresh")
    public Result<OAuth2TokenResp> refresh(@RequestParam("refresh_token") String refreshToken) {
        //第一次刷新refreshToken
        String newRefreshTokenId  = refreshTokenUtils.rotateRefreshToken(refreshToken);
        RefreshTokenInfo info = refreshTokenUtils.getInfoByToken(newRefreshTokenId); // 第二次查询

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", info.getUserId());
        claims.put("username", info.getUsername());
        String newAccessToken = jwtService.generateToken(claims);

        // 令牌轮换：吊销旧的，生成新的 Refresh Token
        //   refreshTokenUtils.revokeRefreshToken(refreshToken);
        return Result.ok(buildTokenResponse(
                newAccessToken,
                info.getUserId(),
                info.getUsername(),
                newRefreshTokenId)
        );
    }

    /**
     * <pre>
     * Scenario: 退出登录使 RefreshToken 失效
     *   Given 持有有效的 refreshToken
     *   When POST /api/auth/logout?refresh_token=xxx
     *   Then refreshToken 从 Redis 中删除（吊销）
     *   And 后续使用该 refreshToken 无法刷新
     * </pre>
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("refresh_token") String refreshToken) {
        refreshTokenUtils.revokeRefreshToken(refreshToken);
        return Result.ok("登出成功");
    }
}