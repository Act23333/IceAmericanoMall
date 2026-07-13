package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.OperationLogEntity;
import org.icedAmericanoMall.domain.vo.OperationLogVO;
import org.mapstruct.Mapper;

/**
 * 操作日志 Entity → VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface OperationLogConverter {

    OperationLogVO toVO(OperationLogEntity entity);
}
