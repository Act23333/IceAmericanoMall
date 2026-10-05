package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务定义 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface TaskDefMapper extends BaseMapper<TaskDefPO> {
}
