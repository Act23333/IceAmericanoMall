package org.icedamericanomall.balance.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.icedamericanomall.balance.domain.Account;
import org.icedamericanomall.balance.domain.AccountRepository;
import org.icedamericanomall.domain.entity.UserEntity;
import org.icedamericanomall.mapper.UserMapper;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * 账户仓储 MyBatis 实现 — Infrastructure 层
 *
 * <pre>
 * 原子性保障:
 *   deductAtomically 使用 UPDATE ... WHERE balance >= #{amount} 条件，
 *   返回 0 行说明余额不足或被并发扣款 → 抛乐观锁异常，上层 @Transactional 回滚。
 * </pre>
 */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final UserMapper userMapper;

    public AccountRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Account findByUserId(Long userId) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getId, userId));
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        return Account.reconstruct(
                user.getId(),
                user.getId(),          // userId = id
                user.getBalance(),
                user.getVersion(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    /** 原子扣款 — 乐观锁保障并发安全 */
    @Override
    public int deductAtomically(Account account, int amount) {
        return userMapper.deductBalance(account.getId(), amount);
    }

    /** 原子充值 */
    @Override
    public int creditAtomically(Account account, int amount) {
        return userMapper.creditBalance(account.getId(), amount);
    }
}
