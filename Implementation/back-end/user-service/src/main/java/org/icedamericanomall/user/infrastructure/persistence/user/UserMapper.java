package org.icedamericanomall.user.infrastructure.persistence.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper —— 余额域已迁出至 wallet-service（ADR-016 收尾），本 Mapper 仅保留
 * MyBatis-Plus BaseMapper 通用能力。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
