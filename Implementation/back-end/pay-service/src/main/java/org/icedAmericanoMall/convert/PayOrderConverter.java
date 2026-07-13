package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.entity.PayOrderEntity;
import org.icedAmericanoMall.domain.vo.PayOrderVO;
import org.mapstruct.Mapper;

/**
 * 支付单 Entity ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PayOrderConverter {

    PayOrderVO toVO(PayOrderEntity entity);
}
