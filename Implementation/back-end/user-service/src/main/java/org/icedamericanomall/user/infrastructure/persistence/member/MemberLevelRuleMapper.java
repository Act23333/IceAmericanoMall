package org.icedamericanomall.user.infrastructure.persistence.member;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员等级规则 Mapper（Phase 1 只读，seed 预置 —— ADR-044）。
 */
@Mapper
public interface MemberLevelRuleMapper extends BaseMapper<MemberLevelRulePO> {
}
