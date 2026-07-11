package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.dto.ReviewCreateReq;
import org.icedAmericanoMall.domain.entity.ReviewEntity;
import org.icedAmericanoMall.domain.vo.ReviewVO;
import org.mapstruct.Mapper;

/**
 * 商品评价 DTO ↔ Entity ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewEntity reqToEntity(ReviewCreateReq req);

    ReviewVO toVO(ReviewEntity entity);
}
