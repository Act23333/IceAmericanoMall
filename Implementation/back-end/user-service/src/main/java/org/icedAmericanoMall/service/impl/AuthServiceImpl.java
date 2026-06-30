package org.icedAmericanoMall.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.mapper.UserMapper;
import org.icedAmericanoMall.service.AuthService;
import org.icedAmericanoMall.service.SmsService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static org.icedAmericanoMall.constants.RedisKeyConstants.SMS_CODE_PREFIX;

@Slf4j
@Service
public class AuthServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements AuthService {

    private final SmsService smsService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SmsService smsService, RedisTemplate<String, String> redisTemplate,
                           PasswordEncoder passwordEncoder) {
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO register(RegisterReqDTO request) {
        String phone = request.getPhone(), username = request.getUsername(), password = request.getPassword();
        smsService.verifyCode(phone, request.getCode());
        checkUserUnique(phone, username);

        UserEntity user = BeanUtils.copyBean(request, UserEntity.class);
        user.setPassword(passwordEncoder.encode(password));
        registerUser(user);
        redisTemplate.delete(SMS_CODE_PREFIX + phone);

        LoginRespDTO resp = BeanUtils.copyBean(user, LoginRespDTO.class);
        resp.setUserId(user.getId());   // 技术PK (Long), 不是 user.getUserId() (String UUID)
        resp.setRole(mapRole(user.getRoleType()));
        return resp;
    }

    @Override
    public LoginRespDTO loginByPassword(PasswordLoginReqDTO req) {
        UserEntity user = StrUtil.isBlank(req.getUsername())
                ? lambdaQuery().eq(UserEntity::getPhone, req.getPhone()).one()
                : lambdaQuery().eq(UserEntity::getUsername, req.getUsername()).one();
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        if (user.getStatus() != UserStatusEnum.NORMAL)
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);

        LoginRespDTO resp = BeanUtils.copyBean(user, LoginRespDTO.class);
        resp.setUserId(user.getId());
        resp.setRole(mapRole(user.getRoleType()));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO loginBySms(SmsLoginReqDTO req) {
        String phone = req.getPhone();
        smsService.verifyCode(phone, req.getCode());

        UserEntity user = lambdaQuery().eq(UserEntity::getPhone, phone).one();
        if (user != null) {
            if (user.getStatus() == UserStatusEnum.FROZEN)
                throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用");
            LoginRespDTO resp = BeanUtils.copyBean(user, LoginRespDTO.class);
            resp.setUserId(user.getId());
            resp.setRole(mapRole(user.getRoleType()));
            return resp;
        }
        // New user auto-register
        user = new UserEntity();
        user.setPhone(phone);
        registerUser(user);
        LoginRespDTO resp = BeanUtils.copyBean(user, LoginRespDTO.class);
        resp.setUserId(user.getId());
        resp.setRole(mapRole(user.getRoleType()));
        return resp;
    }

    private void registerUser(UserEntity user) {
        String userId = UUID.fastUUID().toString();
        if (StrUtil.isBlankIfStr(user.getUsername())) user.setUsername("ice_" + userId);
        user.setUserId(userId);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        if (!save(user)) throw new BizException(ErrorCode.FREQUENT_ERROR, "注册失败");
    }

    private void checkUserUnique(String phone, String username) {
        if (lambdaQuery().eq(UserEntity::getPhone, phone).exists())
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED, "手机号已被注册");
        if (StrUtil.isNotBlank(username) && lambdaQuery().eq(UserEntity::getUsername, username).exists())
            throw new BizException(ErrorCode.USERNAME_ALREADY_TAKEN, "用户名已被占用");
    }

    private String mapRole(Integer roleType) {
        if (roleType == null) return "ROLE_USER";
        return switch (roleType) { case 1 -> "ROLE_SELLER"; case 2 -> "ROLE_ADMIN"; default -> "ROLE_USER"; };
    }
}
