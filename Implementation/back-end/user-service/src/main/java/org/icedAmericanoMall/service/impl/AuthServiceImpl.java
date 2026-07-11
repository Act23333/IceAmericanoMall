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
import org.icedAmericanoMall.dto.ResetPasswordReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.dto.WechatLoginReqDTO;
import org.icedAmericanoMall.integration.wechat.WechatOAuthClient;
import org.icedAmericanoMall.integration.wechat.WechatUserInfo;
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
    private final WechatOAuthClient wechatOAuthClient;

    public AuthServiceImpl(SmsService smsService, RedisTemplate<String, String> redisTemplate,
                           PasswordEncoder passwordEncoder, WechatOAuthClient wechatOAuthClient) {
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.wechatOAuthClient = wechatOAuthClient;
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

        return toLoginResp(user);
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

        return toLoginResp(user);
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
            return toLoginResp(user);
        }
        // New user auto-register
        user = new UserEntity();
        user.setPhone(phone);
        registerUser(user);
        return toLoginResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespDTO loginByWechat(WechatLoginReqDTO req) {
        WechatUserInfo wxUser = wechatOAuthClient.getUserInfoByCode(req.getCode());
        String openid = wxUser.getOpenid();

        UserEntity user = lambdaQuery().eq(UserEntity::getWxOpenid, openid).one();
        if (user != null) {
            if (user.getStatus() == UserStatusEnum.FROZEN) {
                throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用");
            }
            return toLoginResp(user);
        }
        // 新用户：以微信身份自动注册（无手机号/密码）
        user = new UserEntity();
        user.setWxOpenid(openid);
        if (StrUtil.isNotBlank(wxUser.getNickname())) user.setUsername(wxUser.getNickname());
        user.setAvatar(wxUser.getAvatarUrl());
        registerUser(user);
        return toLoginResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordReqDTO request) {
        String phone = request.getPhone();
        smsService.verifyCode(phone, request.getCode());
        UserEntity user = lambdaQuery().eq(UserEntity::getPhone, phone).one();
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "该手机号未注册");
        }
        lambdaUpdate()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getPassword, passwordEncoder.encode(request.getNewPassword()))
                .update();
        redisTemplate.delete(SMS_CODE_PREFIX + phone);
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

    /** 手动构建 LoginRespDTO — 避免 BeanUtils.copyBean 的 String UUID → Long 转换崩溃 */
    private LoginRespDTO toLoginResp(UserEntity user) {
        LoginRespDTO resp = new LoginRespDTO();
        resp.setUserId(user.getId());           // Long 技术PK
        resp.setUsername(user.getUsername());
        resp.setPhone(user.getPhone());
        resp.setRole(mapRole(user.getRoleType()));
        return resp;
    }

    private String mapRole(Integer roleType) {
        if (roleType == null) return "ROLE_USER";
        return switch (roleType) { case 1 -> "ROLE_SELLER"; case 2 -> "ROLE_ADMIN"; default -> "ROLE_USER"; };
    }
}
