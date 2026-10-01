package org.icedamericanomall.user.infrastructure.persistence.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.user.domain.exception.DuplicateUserException;
import org.icedamericanomall.user.domain.model.User;
import org.icedamericanomall.user.domain.model.UserStatusEnum;
import org.icedamericanomall.user.domain.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储实现（基础设施层）。
 *
 * <p>创建走业务键（{@code uk_user_id} / {@code uk_phone} / {@code uk_wx_openid}），
 * 不做"按 id 判断新增还是更新"；持久化框架的唯一键冲突在此翻译为领域异常
 * {@link DuplicateUserException}（领域层不依赖 Spring 异常）。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectById(id)));
    }

    @Override
    public Optional<User> findByIdentity(IdentityTypeEnum identityType, String identifier) {
        if (identityType == null || identifier == null) {
            return Optional.empty();
        }
        return switch (identityType) {
            case PHONE -> findByPhone(identifier);
            case WECHAT, WECHAT_MINI -> findByWxOpenid(identifier);
            // EMAIL / USERNAME / APPLE_ID：user 表无对应列（身份由 user_auth 承载，ADR-008）
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        if (phone == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhone, phone).last("LIMIT 1"))));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username).last("LIMIT 1"))));
    }

    @Override
    public Optional<User> findByWxOpenid(String wxOpenid) {
        if (wxOpenid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getWxOpenid, wxOpenid).last("LIMIT 1"))));
    }

    @Override
    public User insert(User user) {
        UserDO dataObject = UserConverter.toDataObject(user);
        try {
            userMapper.insert(dataObject);
        } catch (DuplicateKeyException e) {
            throw new DuplicateUserException(ErrorCode.USER_ALREADY_EXISTS, "用户标识已存在");
        }
        return UserConverter.toDomain(dataObject);
    }

    @Override
    public int update(User user) {
        return userMapper.updateById(UserConverter.toDataObject(user));
    }

    @Override
    public int updateStatusById(Long id, UserStatusEnum status) {
        return userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .set(UserDO::getStatus, status == null ? null : status.getCode()));
    }

    @Override
    public int updateRoleTypeById(Long id, int roleType) {
        return userMapper.update(null, new LambdaUpdateWrapper<UserDO>()
                .eq(UserDO::getId, id)
                .set(UserDO::getRoleType, roleType));
    }

    @Override
    public int deductBalance(Long id, int amount) {
        return userMapper.deductBalance(id, amount);
    }

    @Override
    public int creditBalance(Long id, int amount) {
        return userMapper.creditBalance(id, amount);
    }

    @Override
    public long countAll() {
        return userMapper.selectCount(null);
    }

    @Override
    public List<User> findPageDescByCreateTime(int offset, int size) {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                        .orderByDesc(UserDO::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(UserConverter::toDomain)
                .toList();
    }
}
