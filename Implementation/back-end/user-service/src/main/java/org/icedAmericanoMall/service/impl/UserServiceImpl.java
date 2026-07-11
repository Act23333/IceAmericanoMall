package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

    @Override
    public void updateProfile(Long userId, UpdateProfileReq req) {
        UserEntity user = lambdaQuery().eq(UserEntity::getId, userId).one();
        if (user == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        var updater = lambdaUpdate().eq(UserEntity::getId, userId);
        if (req.getNickname() != null && !req.getNickname().isBlank())
            updater.set(UserEntity::getUsername, req.getNickname());
        if (req.getAvatar() != null && !req.getAvatar().isBlank())
            updater.set(UserEntity::getAvatar, req.getAvatar());
        updater.update();
    }

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
}
