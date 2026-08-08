package org.icedamericanomall.domain.repository;

import org.icedamericanomall.domain.entity.UserEntity;

import java.util.Optional;

/**
 * 用户仓储接口 — Domain 层定义契约
 *
 * COLA: 接口在 domain 层，实现在 infrastructure 层
 */
public interface UserRepository {

    Optional<UserEntity> findById(Long id);

    /** 原子扣款: UPDATE user SET balance = balance - #{amount} WHERE id = #{id} AND balance >= #{amount} */
    int deductBalanceAtomically(Long id, int amount);

    /** 原子充值 */
    int creditBalanceAtomically(Long id, int amount);

    /** 更新状态 */
    int updateStatus(Long id, int status);

    /** 更新角色 */
    int updateRoleType(Long id, int roleType);
}
