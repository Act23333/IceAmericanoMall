package org.icedamericanomall.user.infrastructure.persistence.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.utils.crypto.BlindIndexUtil;
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
 * <p>创建走业务键（{@code uk_user_id} / {@code uk_phone_hash} / {@code uk_email_hash}），
 * 不做"按 id 判断新增还是更新"；持久化框架的唯一键冲突在此翻译为领域异常
 * {@link DuplicateUserException}（领域层不依赖 Spring 异常）。
 *
 * <p><b>PII 定位（V5.2）</b>：手机号 / 邮箱入参为明文，等值查询一律换算 HMAC 盲索引后
 * 命中 {@code phone_hash} / {@code email_hash}（密文列不可等值比较）；明文列不再读写。
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
            case EMAIL -> findByEmail(identifier);
            // WECHAT / WECHAT_MINI / USERNAME / APPLE_ID：user 表不承载这些标识
            // （身份与凭证归 user_auth 聚合，ADR-008/017）
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        if (phone == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getPhoneHash, BlindIndexUtil.hmac(phone)).last("LIMIT 1"))));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getEmailHash, BlindIndexUtil.hmac(email)).last("LIMIT 1"))));
    }

    @Override
    public Optional<User> findByNickname(String nickname) {
        if (nickname == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(UserConverter.toDomain(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, nickname).last("LIMIT 1"))));
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
