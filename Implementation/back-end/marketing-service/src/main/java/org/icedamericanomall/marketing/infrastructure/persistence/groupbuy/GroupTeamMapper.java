package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 拼团团 Mapper —— {@code group_team} 表通用 CRUD；参团条件更新、满员/过期状态迁移等
 * 显式 SQL 语义由 RepositoryImpl 以 Wrapper 承载。
 */
@Mapper
public interface GroupTeamMapper extends BaseMapper<GroupTeamPO> {
}
