package org.icedamericanomall.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.domain.repository.UserRepository;
import org.icedamericanomall.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户仓储 MyBatis 实现 — Infrastructure 层
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<UserEntity> findById(Long id) {
        UserEntity user = userMapper.selectById(id);
        return Optional.ofNullable(user);
    }

    @Override
    public int deductBalanceAtomically(Long id, int amount) {
        return userMapper.deductBalance(id, amount);
    }

    @Override
    public int creditBalanceAtomically(Long id, int amount) {
        return userMapper.creditBalance(id, amount);
    }

    @Override
    public int updateStatus(Long id, int status) {
        UserEntity u = userMapper.selectById(id);
        if (u == null) return 0;
        u.setStatus(status == 1 ?
                org.icedamericanomall.constants.UserStatusEnum.NORMAL :
                org.icedamericanomall.constants.UserStatusEnum.FROZEN);
        return userMapper.updateById(u);
    }

    @Override
    public int updateRoleType(Long id, int roleType) {
        UserEntity u = userMapper.selectById(id);
        if (u == null) return 0;
        u.setRoleType(roleType);
        return userMapper.updateById(u);
    }
}
