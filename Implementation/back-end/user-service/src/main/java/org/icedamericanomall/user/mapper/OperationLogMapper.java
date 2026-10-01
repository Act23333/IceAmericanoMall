package org.icedamericanomall.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.domain.entity.OperationLogEntity;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
