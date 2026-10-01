package org.icedamericanomall.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.constants.UserStatusEnum;
import org.icedamericanomall.user.convert.UserConverter;
import org.icedamericanomall.user.domain.entity.User;
import org.icedamericanomall.user.domain.entity.UserEntity;
import org.icedamericanomall.user.domain.vo.UserInfoResp;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.infrastructure.persistence.UserRepository;
import org.icedamericanomall.user.mapper.UserMapper;
import org.icedamericanomall.user.service.UserService;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final UserConverter userConverter;
    private final UserMapper userMapper;
    private final UserRepository userRepository;


    @Override
    public UserInfoResp getByUserId(Long userId) {
        UserEntity user = lambdaQuery().eq(UserEntity::getId, userId).one();
        if (Objects.isNull(user)) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (user.getStatus() == UserStatusEnum.FROZEN)
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);
        return userConverter.map(user);
    }

    // updateProfile/patchProfile 已迁移到 UserProfileService（DDD 分层重构 V3.2）

    @Override
    public long countUsers() {
        return lambdaQuery().count();
    }

    @Override
    public IPage<UserInfoResp> pageUsers(int page, int size) {
        IPage<UserEntity> result = lambdaQuery()
                .orderByDesc(UserEntity::getCreateTime)
                .page(new Page<>(page, size));
        return result.convert(userConverter::map);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        UserStatusEnum target = UserStatusEnum.of(status);
        lambdaUpdate().eq(UserEntity::getId, id).set(UserEntity::getStatus, target).update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, Integer roleType) {
        if (roleType == null || roleType < 0 || roleType > 2) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "无效的角色类型: 0=USER, 1=SELLER, 2=ADMIN");
        }
        lambdaUpdate().eq(UserEntity::getId, id).set(UserEntity::getRoleType, roleType).update();
    }

    @Override
    public int getBalance(Long userId) {
        UserEntity user = getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user.getBalance() == null ? 0 : user.getBalance();
    }

    @Override
    public Long saveUser(UserAuthCO userAuthDTO) {
        IdentityTypeEnum identityType = userAuthDTO.getIdentityType();
        User user = switch (identityType) {
            case PHONE -> {
                yield User.builder().phone(userAuthDTO.getIdentifier());
            } case  EMAIL -> {
                yield User.builder().email(userAuthDTO.getIdentifier());
            } default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的登录方式")
        };
        return userRepository.saveUser(user);
    }

    @Transactional
    public Long getOrCreateUser(IdentityTypeEnum type, String identifier) {
        // 1. 先查
        Long userId = userRepository.findByIdentity(type, identifier);
        if (userId != null) {
            return userId;
        }

        // 2. 用分布式锁防止并发创建(可重入锁本质是统一线程嵌套调用
        //可重入锁救不了跨服务死锁，因为可重入的前提是“同一个线程”，而跨服务调用必然换了线程
        String lockKey = "user:create:" + type + ":" + identifier;
        RLock lock = redisson.getLock(lockKey);
        try {
            lock.lock(5, TimeUnit.SECONDS);

            // 3. 双重检查
            userId = userRepository.findByIdentity(type, identifier);
            if (userId != null) {
                return userId;
            }

            // 4. 创建
            User user = User.create(identifier, type);
            userRepository.save(user);
            return user.getId();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<UserAuth> findByIdentityHash(IdentityTypeEnum type, String identifierHash) {
        UserAuthDO doObj = userMapper.selectOne(
                new LambdaQueryWrapper<UserAuthDO>()
                        .eq(UserAuthDO::getIdentityType, type.name())
                        .eq(UserAuthDO::getIdentifierHash, identifierHash)
                        .eq(UserAuthDO::getStatus, 1));
        return Optional.ofNullable(UserAuthConverter.toDomain(doObj));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "扣减金额必须大于0");
        }
        // 原子条件扣减：仅当余额充足才更新，影响行数为 0 即余额不足
        boolean ok = lambdaUpdate()
                .eq(UserEntity::getId, userId)
                .ge(UserEntity::getBalance, amount)
                .setSql("balance = balance - " + amount)
                .update();
        if (!ok) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT, "余额不足");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBalance(Long userId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "金额必须大于0");
        }
        boolean ok = lambdaUpdate()
                .eq(UserEntity::getId, userId)
                .setSql("balance = balance + " + amount)
                .update();
        if (!ok) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }
    }
}
