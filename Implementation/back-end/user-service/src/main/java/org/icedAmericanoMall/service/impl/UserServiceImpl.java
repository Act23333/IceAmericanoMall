package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.constants.UserStatusEnum;
import org.icedAmericanoMall.convert.UserConverter;
import org.icedAmericanoMall.domain.dto.UpdateProfileReq;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.mapper.UserMapper;
import org.icedAmericanoMall.service.UserService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BadRequestException;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final UserConverter userConverter;

    public UserServiceImpl(UserConverter userConverter) {
        this.userConverter = userConverter;
    }

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
