package org.icedamericanomall.marketing.infrastructure.persistence.groupbuy;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 拼团活动 Mapper —— {@code group_buy_activity} 表通用 CRUD（条件更新/时间窗过滤语义
 * 由 RepositoryImpl 以 Wrapper 承载）。
 */
@Mapper
public interface GroupBuyActivityMapper extends BaseMapper<GroupBuyActivityPO> {
}
