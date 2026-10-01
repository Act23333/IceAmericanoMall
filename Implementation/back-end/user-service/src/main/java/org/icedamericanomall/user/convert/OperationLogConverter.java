package org.icedamericanomall.user.convert;

import org.icedamericanomall.user.domain.entity.OperationLogEntity;
import org.icedamericanomall.user.domain.vo.OperationLogVO;
import org.mapstruct.Mapper;

/**
 * 操作日志 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface OperationLogConverter {

    OperationLogVO toVO(OperationLogEntity entity);
}
