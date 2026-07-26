package org.icedamericanomall.convert;

import org.icedamericanomall.domain.dto.CouponCreateReq;
import org.icedamericanomall.domain.dto.FlashBuyResult;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.FlashOrderEntity;
import org.icedamericanomall.domain.entity.FlashSaleEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.domain.vo.CouponVO;
import org.icedamericanomall.domain.vo.FlashBuyVO;
import org.icedamericanomall.domain.vo.FlashOrderVO;
import org.icedamericanomall.domain.vo.FlashSaleVO;
import org.icedamericanomall.domain.vo.UserCouponVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MarketingConverter {

    CouponEntity toEntity(CouponCreateReq req);

    CouponVO toCouponVO(CouponEntity entity);

    List<CouponVO> toCouponVOList(List<CouponEntity> entities);

    UserCouponVO toUserCouponVO(UserCouponEntity entity);

    List<UserCouponVO> toUserCouponVOList(List<UserCouponEntity> entities);

    FlashSaleVO toFlashSaleVO(FlashSaleEntity entity);

    List<FlashSaleVO> toFlashSaleVOList(List<FlashSaleEntity> entities);

    FlashBuyVO toFlashBuyVO(FlashBuyResult result);

    FlashOrderVO toFlashOrderVO(FlashOrderEntity entity);
}
