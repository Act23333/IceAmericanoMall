package org.icedamericanomall.marketing.infrastructure.persistence.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务完成记录 Mapper —— 入参/出参均为 PO；唯一键冲突由 MySQL 上抛、Spring 翻译为
 * {@code DuplicateKeyException}。
 */
@Mapper
public interface TaskRecordMapper extends BaseMapper<TaskRecordPO> {
}
