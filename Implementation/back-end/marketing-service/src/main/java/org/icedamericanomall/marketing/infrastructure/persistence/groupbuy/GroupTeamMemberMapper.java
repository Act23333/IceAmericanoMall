package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 拼团团成员 Mapper —— {@code group_team_member} 表通用 CRUD（幂等回落语义由应用层承担）。
 */
@Mapper
public interface GroupTeamMemberMapper extends BaseMapper<GroupTeamMemberPO> {
}
