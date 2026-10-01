package org.icedamericanomall.user.infrastructure.persistence.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.icedamericanomall.user.infrastructure.persistence.user.UserDO;


@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    /**
     * 原子扣款 — 乐观锁保障并发安全。
     *
     * SQL: UPDATE user SET balance = balance - #{amount}, version = version + 1
     *      WHERE id = #{id} AND balance >= #{amount}
     *
     * @return 影响行数（1=成功, 0=余额不足或版本冲突）
     */
    @Update("UPDATE user SET balance = balance - #{amount}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id} AND balance >= #{amount}")
    int deductBalance(@Param("id") Long id, @Param("amount") int amount);

    /**
     * 原子充值。
     *
     * SQL: UPDATE user SET balance = balance + #{amount}, version = version + 1
     *      WHERE id = #{id}
     *
     * @return 影响行数
     */
    @Update("UPDATE user SET balance = balance + #{amount}, version = version + 1, update_time = NOW() "
            + "WHERE id = #{id}")
    int creditBalance(@Param("id") Long id, @Param("amount") int amount);
}
