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
     * 用户登录接口方法
     * @return 用户登录信息
     * 为什么要将密码登录和手机号登录放到一个方法中，而不是分开两个接口，根据前端应对用户行为进行调用呢
     *  增加了前端的复杂度，前端需要根据用户的行为选择请求对应的接口
     *  因为前端需要绑定后端的方法，所以扩展性下降，如果使用一个登录方法，前端不需要知道具体逻辑，后端修改，前端不用修改，扩展性增加
     *  且后端的复杂度增加了，应为需要单独给每个接口增加限流和安全防控
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

    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("refresh_token") String refreshToken) {
        refreshTokenUtils.revokeRefreshToken(refreshToken);
        return Result.ok("登出成功");
    }
}