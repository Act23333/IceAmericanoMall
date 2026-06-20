package org.icedAmericanoMall.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.SmsCodeSendReq;
import org.icedAmericanoMall.service.SmsService;
import org.noLazy.common.client.captcha.CaptchaClient;
import org.noLazy.common.client.sms.SmsClient;
import org.noLazy.common.dto.Geetest4ValidateRequest;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.exception.UnauthorizedException;
import org.noLazy.common.utils.BeanUtils;
import org.noLazy.common.utils.SmsCodeGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.icedAmericanoMall.constants.RedisKeyConstants.SMS_CODE_PREFIX;
import static org.icedAmericanoMall.constants.RedisKeyConstants.SMS_LIMIT_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final CaptchaClient<Geetest4ValidateRequest> captchaClient;
    private final SmsClient smsClient;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void sendCode(SmsCodeSendReq req) {
        String phone = req.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号不能为空");
        }
        // 人机验证
        Geetest4ValidateRequest geetestReq = BeanUtils.copyBean(req, Geetest4ValidateRequest.class);
        if (!captchaClient.verify(geetestReq)) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证失败");
        }
        // 频率限制
        String limitKey = SMS_LIMIT_PREFIX + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new UnauthorizedException(ErrorCode.FREQUENT_ERROR, "发送操作太频繁，请稍后再试");
        }
        // 生成验证码
        String code = SmsCodeGenerator.generateCode(6);
        String captchaKey = SMS_CODE_PREFIX + phone;
        redisTemplate.delete(captchaKey);
        redisTemplate.opsForValue().set(captchaKey, code, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);
        // 异步发送
        CompletableFuture.runAsync(() -> {
            try {
                smsClient.send(phone, code);
            } catch (Exception e) {
                redisTemplate.delete(captchaKey);
                redisTemplate.delete(limitKey);
                log.error("短信发送失败，phone={}", phone, e);
            }
        });
    }

    @Override
    public void verifyCode(String phone, String code) {
        String cached = redisTemplate.opsForValue().get(SMS_CODE_PREFIX + phone);
        if (cached == null) throw new BizException(ErrorCode.CAPTCHA_EXPIRED, "验证码已过期或未发送");
        if (!cached.equals(code)) throw new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误");
    }
}
