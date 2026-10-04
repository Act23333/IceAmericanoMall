package org.icedamericanomall.user.infrastructure.persistence.oplog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.icedamericanomall.user.infrastructure.persistence.oplog.OperationLogPO;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogPO> {
}
