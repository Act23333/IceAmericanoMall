package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.domain.model.review.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewVO toVO(Review review);
}