package org.icedamericanomall.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.constants.UserStatusEnum;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.dto.LoginRespDTO;
import org.icedamericanomall.dto.PasswordLoginReqDTO;
import org.icedamericanomall.dto.RegisterReqDTO;
import org.icedamericanomall.dto.ResetPasswordReqDTO;
import org.icedamericanomall.dto.SmsLoginReqDTO;
import org.icedamericanomall.dto.WechatLoginReqDTO;
import org.icedamericanomall.integration.wechat.WechatOAuthClient;
import org.icedamericanomall.integration.wechat.WechatUserInfo;
import org.icedamericanomall.mapper.PermissionMapper;
import org.icedamericanomall.mapper.RoleMapper;
import org.icedamericanomall.service.PointsService;
import org.icedamericanomall.mapper.UserMapper;
import org.icedamericanomall.service.AuthService;
import org.icedamericanomall.service.SmsService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


import static org.icedamericanomall.constants.RedisKeyConstants.SMS_CODE_PREFIX;

@Slf4j
@Service
public class AuthServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements AuthService {

    private final SmsService smsService;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final WechatOAuthClient wechatOAuthClient;
    private final PointsService pointsService;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    public AuthServiceImpl(SmsService smsService, RedisTemplate<String, String> redisTemplate,
                           PasswordEncoder passwordEncoder, WechatOAuthClient wechatOAuthClient,
                           PointsService pointsService, RoleMapper roleMapper, PermissionMapper permissionMapper) {
        this.smsService = smsService;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.wechatOAuthClient = wechatOAuthClient;
        this.pointsService = pointsService;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
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
        LoginRespDTO r;
        boolean isNew = false;
        if (user != null) {
            if (user.getStatus() == UserStatusEnum.FROZEN)
                throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用");
            r = toLoginResp(user);
        } else {
            user = new UserEntity();
            user.setPhone(phone);
            user.setPassword(passwordEncoder.encode(UUID.fastUUID().toString())); // DB NOT NULL 兼容
            registerUser(user);
            r = toLoginResp(user);
            isNew = true;
        }
        awardLoginBonus(user, isNew);
        return r;
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
        LoginRespDTO r;
        boolean isNew = false;
        if (user != null) {
            if (user.getStatus() == UserStatusEnum.FROZEN)
                throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用");
            r = toLoginResp(user);
        } else {
            user = new UserEntity();
            user.setWxOpenid(openid);
            if (StrUtil.isNotBlank(wxUser.getNickname())) user.setUsername(wxUser.getNickname());
            user.setAvatar(wxUser.getAvatarUrl());
            user.setPassword(passwordEncoder.encode(UUID.fastUUID().toString())); // DB NOT NULL 兼容
            registerUser(user);
            r = toLoginResp(user);
            isNew = true;
        }
        awardLoginBonus(user, isNew);
        return r;
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

    /** 首次/回归登录奖励（V2.5）：首次 200 分，回归（距上次登录 >30天）100 分。 */
    private void awardLoginBonus(UserEntity user, boolean isNew) {
        LocalDateTime now = LocalDateTime.now();
        int points = 0;
        if (isNew) {
            points = 200;
        } else if (user.getLastLoginTime() != null &&
                user.getLastLoginTime().plusDays(30).isBefore(now)) {
            points = 100;
        }
        if (points > 0) {
            try {
                pointsService.addPoints(user.getId(), points, 2,
                        isNew ? "首次登录奖励" : "回归登录奖励");
            } catch (Exception e) {
                log.warn("登录奖励发放失败: userId={}", user.getId(), e);
            }
        }
        // 更新最近登录时间
        lambdaUpdate().eq(UserEntity::getId, user.getId())
                .set(UserEntity::getLastLoginTime, now).update();
    }


    /** 默认角色ID — 新用户注册时自动分配 ROLE_USER */
    private static final Long DEFAULT_ROLE_ID = 1L;

    private void registerUser(UserEntity user) {
        String userId = UUID.fastUUID().toString(true); // true=无连字符，固定32位
        if (StrUtil.isBlankIfStr(user.getUsername())) user.setUsername("ice_" + userId);
        user.setUserId(userId);
        // createTime/updateTime/registerTime → @TableField + MyMetaObjectHandler 自动填充
        if (!save(user)) throw new BizException(ErrorCode.FREQUENT_ERROR, "注册失败");

        // RBAC: 新用户默认分配 ROLE_USER (id=1)
        roleMapper.insertUserRole(user.getId(), DEFAULT_ROLE_ID);
    }

    private void checkUserUnique(String phone, String username) {
        if (lambdaQuery().eq(UserEntity::getPhone, phone).exists())
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED, "手机号已被注册");
        if (StrUtil.isNotBlank(username) && lambdaQuery().eq(UserEntity::getUsername, username).exists())
            throw new BizException(ErrorCode.USERNAME_ALREADY_TAKEN, "用户名已被占用");
    }

    /** 手动构建 LoginRespDTO — 聚合 RBAC 五表模型的角色+权限 */
    private LoginRespDTO toLoginResp(UserEntity user) {
        LoginRespDTO resp = new LoginRespDTO();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());

        // 从 RBAC 五表模型加载角色+权限
        Set<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        Set<String> perms = new HashSet<>();
        perms.addAll(permissionMapper.selectDirectPermCodesByUserId(user.getId()));
        perms.addAll(permissionMapper.selectRolePermCodesByUserId(user.getId()));
        resp.setRoles(roles);
        resp.setPermissions(perms);

        // 主角色 (兼容旧字段，取最高权限角色)
        String primaryRole = mapRole(user.getRoleType());
        if (roles.contains("ROLE_ADMIN")) primaryRole = "ROLE_ADMIN";
        else if (roles.contains("ROLE_SELLER")) primaryRole = "ROLE_SELLER";
        resp.setRole(primaryRole);

        return resp;
    }

    private String mapRole(Integer roleType) {
        if (roleType == null) return "ROLE_USER";
        return switch (roleType) { case 1 -> "ROLE_SELLER"; case 2 -> "ROLE_ADMIN"; default -> "ROLE_USER"; };
    }
}
