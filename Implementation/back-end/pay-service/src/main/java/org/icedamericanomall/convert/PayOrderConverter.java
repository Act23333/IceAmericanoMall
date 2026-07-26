package org.icedamericanomall.convert;

import org.icedamericanomall.domain.entity.PayOrderEntity;
import org.icedamericanomall.domain.vo.PayOrderVO;
import org.mapstruct.Mapper;

/**
 * 支付单 Entity ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PayOrderConverter {

    PayOrderVO toVO(PayOrderEntity entity);
}
