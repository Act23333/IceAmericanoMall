package org.icedamericanomall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.domain.entity.OperationLogEntity;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
