package org.icedamericanomall.marketing.adapter.assembler;

import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.adapter.response.PointsBalanceVO;
import org.icedamericanomall.marketing.adapter.response.PointsLogVO;
import org.icedamericanomall.marketing.adapter.response.SignResultVO;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.app.dto.SignResultDTO;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 营销适配层装配器（Assembler）—— 请求对象 / 领域聚合 / 应用层 DTO ↔ 视图对象。
 *
 * <p>出参一律为 VO（聚合不泄漏到适配层之外）；入参 {@link CouponCreateReq} → 领域聚合在此完成，
 * 业务归一化由聚合自身负责（请求对象只做校验绑定）。
 *
 * <p>UNMAPPED 忽略口径：{@code id}/{@code issuedQty}/{@code sellerId}/审计字段由基础设施回填；
 * coupon 的 {@code scope*} 列历史上未映射到聚合（见数据模型），VO 对应字段恒为空。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketingConverter {

    /** 创建请求 → 优惠券聚合（issuedQty 固定置 0，由创建编排负责）。 */
    Coupon toDomain(CouponCreateReq req);

    /** 优惠券聚合 → 视图对象。 */
    CouponVO toVO(Coupon coupon);

    /** 优惠券聚合列表 → 视图对象列表（列表方法按类型区分命名，避免泛型疑符冲突）。 */
    List<CouponVO> toCouponVOList(List<Coupon> coupons);

    /** 用户持券聚合 → 视图对象。 */
    UserCouponVO toVO(UserCoupon userCoupon);

    /** 用户持券聚合列表 → 视图对象列表。 */
    List<UserCouponVO> toUserCouponVOList(List<UserCoupon> userCoupons);

    /** 秒杀聚合 → 视图对象。 */
    FlashSaleVO toVO(FlashSale flashSale);

    /** 秒杀聚合列表 → 视图对象列表。 */
    List<FlashSaleVO> toFlashSaleVOList(List<FlashSale> flashSales);

    /** 秒杀购买结果 → 视图对象。 */
    FlashBuyVO toFlashBuyVO(FlashBuyResult result);

    /** 签到结果 DTO → 视图对象。 */
    SignResultVO toSignResultVO(SignResultDTO dto);

    /** 积分账本聚合 → 视图对象。 */
    PointsLogVO toPointsLogVO(PointsLog pointsLog);

    /**
     * 积分余额（userId + 余额两个标量）→ 视图对象。
     *
     * <p>两个入参没有公共源对象，故用 default 方法显式装配（MapStruct 不为其生成实现）；
     * 装配逻辑仍统一收在适配层转换器中，控制器不手写转换。
     *
     * @param userId  当前登录用户ID
     * @param balance 积分总余额
     * @return 积分余额视图对象
     */
    default PointsBalanceVO toPointsBalanceVO(Long userId, long balance) {
        PointsBalanceVO vo = new PointsBalanceVO();
        vo.setUserId(userId);
        vo.setBalance(balance);
        return vo;
    }
}
