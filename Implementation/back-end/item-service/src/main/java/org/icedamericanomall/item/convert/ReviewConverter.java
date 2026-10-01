package org.icedamericanomall.item.convert;

import org.icedamericanomall.item.domain.dto.ReviewCreateReq;
import org.icedamericanomall.item.domain.entity.ReviewEntity;
import org.icedamericanomall.item.domain.vo.ReviewVO;
import org.mapstruct.Mapper;

/**
 * 商品评价 DTO ↔ Entity ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewEntity reqToEntity(ReviewCreateReq req);

    ReviewVO toVO(ReviewEntity entity);
}
