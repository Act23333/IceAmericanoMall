package org.icedAmericanoMall.service.impl;

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
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public UserInfoResp getByUserId(Long userId) {
        UserEntity user = lambdaQuery().eq(UserEntity::getId, userId).one();
        if (Objects.isNull(user)) throw new BizException(ErrorCode.USER_NOT_FOUND);
        if (user.getStatus() == UserStatusEnum.FROZEN)
            throw new BizException(ErrorCode.USER_STATUS_ABNORMAL);
        return UserConverter.INSTANCE.map(user);
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
}
