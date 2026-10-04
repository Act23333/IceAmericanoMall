package org.icedamericanomall.wallet.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 钱包账户 Mapper —— 入参/出参均为 PO 或基本类型。
 *
 * <p>扣减/入账均为单语句原子操作（迁出前 user 表同构实现，ADR-005）：
 * 扣减条件 {@code balance >= amount} 承担并发安全；入账 upsert 兜底初始化账户行。
 */
@Mapper
public interface WalletAccountMapper {

    @Select("SELECT balance FROM wallet_account WHERE user_id = #{userId}")
    Integer selectBalanceByUserId(@Param("userId") Long userId);

    @Update("UPDATE wallet_account SET balance = balance - #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND balance >= #{amount}")
    int deduct(@Param("userId") Long userId, @Param("amount") int amount);

    @Insert("INSERT INTO wallet_account (user_id, balance, create_time, update_time) "
            + "VALUES (#{userId}, #{amount}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE balance = balance + #{amount}, update_time = NOW()")
    int credit(@Param("userId") Long userId, @Param("amount") int amount);
}
