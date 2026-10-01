package org.icedamericanomall.pay.convert;

import org.icedamericanomall.pay.domain.entity.PayOrderEntity;
import org.icedamericanomall.pay.domain.vo.PayOrderVO;
import org.mapstruct.Mapper;

/**
 * 支付单 Entity ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PayOrderConverter {

    PayOrderVO toVO(PayOrderEntity entity);
}
