package org.icedAmericanoMall.convert;

import org.icedAmericanoMall.domain.dto.CouponCreateReq;
import org.icedAmericanoMall.domain.dto.FlashBuyResult;
import org.icedAmericanoMall.domain.entity.CouponEntity;
import org.icedAmericanoMall.domain.entity.FlashOrderEntity;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.domain.entity.UserCouponEntity;
import org.icedAmericanoMall.domain.vo.CouponVO;
import org.icedAmericanoMall.domain.vo.FlashBuyVO;
import org.icedAmericanoMall.domain.vo.FlashOrderVO;
import org.icedAmericanoMall.domain.vo.FlashSaleVO;
import org.icedAmericanoMall.domain.vo.UserCouponVO;
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
