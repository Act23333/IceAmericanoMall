package org.icedamericanomall.pay.adapter.assembler;

import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.mapstruct.Mapper;

/**
 * 支付单 Domain ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PayOrderConverter {

    PayOrderVO toVO(PayOrder entity);
}
