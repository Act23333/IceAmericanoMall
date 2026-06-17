package org.icedAmericanoMall.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.icedAmericanoMall.service.UserService;
import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.convert.UserConverter;
import org.icedAmericanoMall.domain.dto.SmsCodeSendReq;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.dto.LoginRespDTO;
import org.icedAmericanoMall.dto.PasswordLoginReqDTO;
import org.icedAmericanoMall.dto.RegisterReqDTO;
import org.icedAmericanoMall.dto.SmsLoginReqDTO;
import org.icedAmericanoMall.mapper.UserMapper;
import org.noLazy.common.client.captcha.CaptchaClient;
import org.noLazy.common.client.sms.SmsClient;
import org.noLazy.common.dto.Geetest4ValidateRequest;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.exception.UnauthorizedException;
import org.noLazy.common.utils.BeanUtils;
import org.noLazy.common.utils.SmsCodeGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.icedAmericanoMall.constants.RedisKeyConstants.SMS_CODE_PREFIX;
import static org.icedAmericanoMall.constants.RedisKeyConstants.SMS_LIMIT_PREFIX;

/**
 * @ClassName: UserServiceImpl
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/17 13:11
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.service.impl
 * TODO:
 *  登录与注册：
 *      登录：如果用户不存在，直接使用手机号注册（有默认的用户配置，如用户名为XXX.ice，登录可以使用手机号，用户名登录
 *      注册：用户注册不止于手机号和验证码，可以添加用户名，密码等额外信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final CaptchaClient<Geetest4ValidateRequest> captchaClient;
    private final SmsClient smsClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;   // BCryptPasswordEncoder

//    /**
//     * 用户登录（短信验证码方式）
//     */
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public UserLoginVO login(LoginFormDTO loginFormDTO) {
//        String phone = loginFormDTO.getPhone();
//        String code = loginFormDTO.getCode();
//
//        // 1. 校验验证码（校验后立即删除，防止重复使用）
//        verifySmsCode(phone, code);
//
//        // 2. 查询用户
//        UserEntity user = lambdaQuery()
//                .eq(UserEntity::getPhone, phone)
//                .one();
//
//        if (user != null && user.getStatus() != UserStatusEnum.FROZEN) {
//            throw new UnauthorizedException(ErrorCode.FORBIDDEN, "用户已被冻结");
//        } else {
//            user = new UserEntity();
//            user.setPhone(phone);
//            registerUser(user);
//        }
//
//        // 3. 生成登录凭证（例如 JWT）
//        String token = generateToken(user);
//
//        log.info("用户登录成功，手机号：{}", phone);
//        redisTemplate.delete(SMS_CODE_PREFIX + phone);
//        return BeanMapper.map(user, UserLoginVO.class);
//    }


    /**
     * <pre>
     * Scenario: 手机号+验证码注册成功
     *   Given 手机号未被注册
     *   And 验证码在 Redis 中存在且未过期
     *   And 人机验证已通过（sendSmsCode时校验）
     *   When 用户提交注册请求
     *   Then 创建用户记录，状态为"正常"
     *   And 密码 BCrypt 加密存储
     *   And 返回 LoginRespDTO 含用户信息
     *   And 验证码从 Redis 中删除
     *
     * Scenario: 重复手机号注册
     *   Given 手机号已被注册
     *   When 用户提交注册请求
     *   Then 抛出 BizException "手机号已注册"
     * </pre>
     */
    @Override
    public LoginRespDTO register(RegisterReqDTO request) {
        String phone = request.getPhone();
        String username = request.getUsername();
        String password = request.getPassword();
        String code = request.getCode();

        // 1. 校验验证码
        verifySmsCode(phone, code);

        // 2. 检查手机号和用户名是否唯一
        checkUserUnique(phone, username);

        // 3. 加密密码并构建用户实体
        UserEntity userEntity = BeanUtils.copyBean(request, UserEntity.class);
        userEntity.setPassword(passwordEncoder.encode(password));
        // 4. 保存用户
        registerUser(userEntity);
        // 5. 删除验证码
        redisTemplate.delete(SMS_CODE_PREFIX + phone);
        log.info("用户注册成功，手机号：{}，用户名：{}", phone, username);
        LoginRespDTO resp = BeanUtils.copyBean(userEntity, LoginRespDTO.class);
        resp.setRole(mapRole(userEntity.getRoleType()));
        return resp;
    }


    /**
     * <pre>
     * Scenario: 发送短信验证码
     *   Given 手机号格式正确
     *   And 极验人机验证通过
     *   And 60秒内未重复发送
     *   When 用户请求发送验证码
     *   Then 生成6位验证码存储到 Redis（5分钟有效）
     *   And 异步调用短信服务发送
     *   And 设置60秒发送间隔限制
     *
     * Scenario: 人机验证失败拒绝发送
     *   Given 极验校验未通过
     *   When 用户请求发送验证码
     *   Then 抛出 BizException "人机验证失败"
     *
     * Scenario: 频率限制
     *   Given 60秒内已发送过验证码
     *   When 用户再次请求发送
     *   Then 抛出 UnauthorizedException "发送操作太频繁"
     * </pre>
     */
    @Override
    public void sendSmsCode(SmsCodeSendReq smsCodeSendReq) {
        String phone = smsCodeSendReq.getPhone();
//        //0.  优先检测用户状态
//        //禁用用户直接拒绝验证和发送验证码，
//        UserEntity userEntity = lambdaQuery().eq(UserEntity::getPhone, phone).one();
//        if (userEntity != null && userEntity.getStatus() == UserStatusEnum.FROZEN) {
//            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用，无法获取验证码");
//        }
        // 1. 人机验证
        Geetest4ValidateRequest geetestRequest = BeanUtils.copyBean(smsCodeSendReq, Geetest4ValidateRequest.class);
        boolean verify = captchaClient.verify(geetestRequest);
        if (!verify) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "人机验证失败");
        }

        //防御性编程，分布式项目强制要求
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号不能为空");
        }
        // 2. 频率限制
        String limitKey = SMS_LIMIT_PREFIX + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            throw new UnauthorizedException(ErrorCode.FREQUENT_ERROR, "发送操作太频繁，请稍后再试");
        }

        // 3. 生成验证码并存储到 Redis（有效期 5 分钟）
        String code = SmsCodeGenerator.generateCode(6);
        String captchaKey = SMS_CODE_PREFIX + phone;
        //发送新验证码，先删除旧验证码 → 旧码立即失效（解决你的安全顾虑）
        redisTemplate.delete(captchaKey);
        redisTemplate.opsForValue().set(captchaKey, code, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);

        // 4. 异步发送短信（避免阻塞）
        CompletableFuture.runAsync(() -> {
            try {
                smsClient.send(phone, code);
                log.info("短信验证码发送成功 → 手机号：{}", phone);
            } catch (Exception e) {
                log.error("短信发送失败，手机号：{}", phone, e);
                //发送失败，删除无效验证码，允许用户重新发送
                redisTemplate.delete(captchaKey);
                redisTemplate.delete(limitKey);
                log.error("短信发送失败，手机号：{}", phone, e);
            }
        });
    }

    /**
     * <pre>
     * Scenario: 密码登录成功
     *   Given 手机号/用户名已注册，状态正常
     *   And 密码 BCrypt 校验通过
     *   When 用户提交正确的密码
     *   Then 返回 LoginRespDTO 含用户ID、用户名、角色
     *
     * Scenario: 密码错误
     *   Given 手机号/用户名已注册
     *   When 用户提交错误的密码
     *   Then 抛出 BizException "手机号或密码错误"
     *
     * Scenario: 账号被禁用
     *   Given 用户状态为"禁用"
     *   When 用户尝试登录
     *   Then 抛出 BizException "账号已被禁用"
     * </pre>
     */
    @Override
    public LoginRespDTO loginByPassword(PasswordLoginReqDTO passwordLoginReqDTO) {
        //request不用校验，判断为手机号登录还是用户名登录,然后判断是否存在，存在则返回对象并校验验证码
        String username = passwordLoginReqDTO.getUsername();
        String phone = passwordLoginReqDTO.getPhone();
        String password = passwordLoginReqDTO.getPassword();
        UserEntity userEntity;
        //这里要做高并发，如果大量用户频繁点击（刚好网络波动等），可能会导致数据库压力增大，且业务会重复执行
        //前端登录键置灰，或者用户点击登录直接进入等待页面
        //布隆过滤器（也是防止缓存击穿的好手）
        //频率限制接口限流
        //锁：分布式锁、乐观锁、分段锁
        //数据库的唯一键天生就是幂等的，但这是最终手段
        if (Strings.isBlank(username)) {
            userEntity = lambdaQuery().eq(UserEntity::getPhone, phone).one();
        } else
            userEntity = lambdaQuery().eq(UserEntity::getUsername, username).one();
        if (Objects.isNull(userEntity)) {
            //密码登录用户不存在不能登录，返回用户不存在！
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        //不为空，用户存在，需要判断密码是否匹配，为保证密码不被泄露，使用spring-boot-security的PasswordEncode
        boolean flag = passwordEncoder.matches(password, userEntity.getPassword());

        if (!flag) {
            //密码不匹配，返回密码错误
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        //用户存在，且密码正确
        //   登录接口 → 再次检测用户状态
        //防止用户状态实时变更（比如刚获取验证码就被管理员禁用）
        if (userEntity.getStatus() == UserStatusEnum.NORMAL) {
            LoginRespDTO resp = BeanUtils.copyBean(userEntity, LoginRespDTO.class);
            resp.setRole(mapRole(userEntity.getRoleType()));
            return resp;
        }
        throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);
    }

    /**
     * <pre>
     * Scenario: 验证码登录（老用户）
     *   Given 手机号已注册且状态正常
     *   And 验证码校验通过
     *   When 用户提交验证码登录请求
     *   Then 直接登录成功，返回 LoginRespDTO
     *
     * Scenario: 验证码登录（新用户自动注册）
     *   Given 手机号未注册
     *   And 验证码校验通过
     *   When 用户提交验证码登录请求
     *   Then 自动创建用户（默认用户名 ice_xxx）
     *   And 返回 LoginRespDTO
     * </pre>
     */
    @Override
    public LoginRespDTO loginBySms(SmsLoginReqDTO smsLoginReqDTO) {
        //手机验证码登录流程：从redis获取验证码，并与传入的用户验证码进行校验
        String phone = smsLoginReqDTO.getPhone();
        String code = smsLoginReqDTO.getCode();
//        if (Objects.isNull(correctCode)) {
//            //如果验证码为空,可能是验证码过期或者没有验证码
//            throw new BizException(ErrorCode.CAPTCHA_EXPIRED);
//        }
//        if (!code.equals(correctCode)) {
//            //如果验证码验证失败
//            throw new BizException(ErrorCode.CAPTCHA_ERROR);
//        }
        verifySmsCode(phone, code);
        //从数据库中查询是否存在该用户
        UserEntity userEntity = lambdaQuery().eq(UserEntity::getPhone, phone).one();
        if (Objects.nonNull(userEntity)) {
            //  关键：老用户必须校验状态
            if (userEntity.getStatus() == UserStatusEnum.FROZEN) {
                throw new BizException(ErrorCode.USER_STATUS_ABNORMAL, "账号已被禁用，无法登录");
            }
            // 状态正常，直接返回登录信息
            LoginRespDTO resp = BeanUtils.copyBean(userEntity, LoginRespDTO.class);
            resp.setRole(mapRole(userEntity.getRoleType()));
            return resp;
        }
        //如果不存在，这说明用户第一次登录，使用默认值并注册
        UserEntity user = new UserEntity();
        user.setPhone(phone);
        registerUser(user);
        LoginRespDTO resp = BeanUtils.copyBean(user, LoginRespDTO.class);
        resp.setRole(mapRole(userEntity.getRoleType()));
        return resp;
    }

    @Override
    public UserInfoResp getByUserId(Long userId) {
        UserEntity userEntity = lambdaQuery().eq(UserEntity::getId, userId).one();
        if (Objects.isNull(userEntity)) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (userEntity.getStatus() == UserStatusEnum.FROZEN) {
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);
        }
        return UserConverter.INSTANCE.map(userEntity);
    }

    /**
     * <pre>
     * Scenario: 用户更新个人资料
     *   Given 持有有效 JWT
     *   When PUT /user/profile with {nickname, avatar}
     *   Then 仅更新非空字段（null-safe 部分更新）
     *   And 返回成功
     * </pre>
     */
    @Override
    public void updateProfile(Long userId, UpdateProfileReq req) {
        UserEntity user = lambdaQuery().eq(UserEntity::getId, userId).one();
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        var updater = lambdaUpdate().eq(UserEntity::getId, userId);
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            updater.set(UserEntity::getUsername, req.getNickname());
        }
        if (req.getAvatar() != null && !req.getAvatar().isBlank()) {
            updater.set(UserEntity::getAvatar, req.getAvatar());
        }
        updater.update();
    }

    @Override
    public long countUsers() {
        return lambdaQuery().count();
    }

    private String mapRole(Integer roleType) {
        if (roleType == null) return "ROLE_USER";
        return switch (roleType) {
            case 1 -> "ROLE_SELLER";
            case 2 -> "ROLE_ADMIN";
            default -> "ROLE_USER";
        };
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验验证码并在校验通过后删除
     */
    private void verifySmsCode(String phone, String code) {
        String key = SMS_CODE_PREFIX + phone;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            throw new BizException(ErrorCode.CAPTCHA_EXPIRED, "验证码已过期或未发送");
        }
        if (!cachedCode.equals(code)) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "验证码错误");
        }
    }

    /**
     * 检查手机号和用户名是否已存在
     */
    private void checkUserUnique(String phone, String username) {
        boolean phoneExists = lambdaQuery().eq(UserEntity::getPhone, phone).exists();
        boolean usernameExists = StrUtil.isNotBlank(username) && lambdaQuery().eq(UserEntity::getUsername, username).exists();

        if (phoneExists && usernameExists) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "手机号和用户名均已被注册");
        }
        if (phoneExists) {
            throw new BizException(ErrorCode.PHONE_ALREADY_REGISTERED, "手机号已被注册");
        }
        if (usernameExists) {
            throw new BizException(ErrorCode.USERNAME_ALREADY_TAKEN, "用户名已被占用");
        }
    }

    /**
     * 初始化【自动注册】用户默认属性
     */
    private void initUser(UserEntity user) {
        String userId = UUID.fastUUID().toString();
        // 默认用户名：ice_userId
        if (StrUtil.isBlankIfStr(user.getUsername())) user.setUsername("ice_" + userId);
        // 默认用户业务唯一标识
        user.setUserId(userId);
        // 默认密码（123456 加密），支持后续账号密码登录
        // 不设置默认密码，防止安全隐患
        //user.setPassword(passwordEncoder.encode("123456"));
        // 账号状态：正常
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 生成登录凭证（JWT 示例）
     */
    private String generateToken(UserEntity user) {
        // 实际使用 JWT 或其它 Token 生成逻辑
        // 这里简化返回用户ID的Base64编码
        return java.util.Base64.getEncoder().encodeToString((user.getId().toString()).getBytes());
    }

    /**
     * 初始化用户信息，并写入到数据库总
     *
     * @param userEntity
     */
    private void registerUser(UserEntity userEntity) {
        //TODO：手机号注册，默认设置用户属性
        initUser(userEntity);
        boolean saved = save(userEntity);
        if (!saved) {
            throw new UnauthorizedException(ErrorCode.FREQUENT_ERROR, "注册失败，请稍后重试");
        }
    }

}