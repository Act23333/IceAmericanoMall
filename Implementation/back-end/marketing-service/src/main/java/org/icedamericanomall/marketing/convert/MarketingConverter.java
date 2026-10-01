package org.icedamericanomall.marketing.convert;

import org.icedamericanomall.marketing.domain.dto.CouponCreateReq;
import org.icedamericanomall.marketing.domain.dto.FlashBuyResult;
import org.icedamericanomall.marketing.domain.entity.CouponEntity;
import org.icedamericanomall.marketing.domain.entity.FlashOrderEntity;
import org.icedamericanomall.marketing.domain.entity.FlashSaleEntity;
import org.icedamericanomall.marketing.domain.entity.UserCouponEntity;
import org.icedamericanomall.marketing.domain.vo.CouponVO;
import org.icedamericanomall.marketing.domain.vo.FlashBuyVO;
import org.icedamericanomall.marketing.domain.vo.FlashOrderVO;
import org.icedamericanomall.marketing.domain.vo.FlashSaleVO;
import org.icedamericanomall.marketing.domain.vo.UserCouponVO;
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
