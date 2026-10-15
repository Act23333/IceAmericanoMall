package org.icedamericanomall.marketing.adapter.assembler;

import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.adapter.response.ActivityVO;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.adapter.response.GroupTeamVO;
import org.icedamericanomall.marketing.adapter.response.PointsBalanceVO;
import org.icedamericanomall.marketing.adapter.response.PointsLogVO;
import org.icedamericanomall.marketing.adapter.response.PromotionVO;
import org.icedamericanomall.marketing.adapter.response.SignResultVO;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.app.dto.SignResultDTO;
import org.icedamericanomall.marketing.domain.enums.PromotionTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 营销适配层装配器（Assembler）—— 请求对象 / 领域聚合 / 应用层 DTO ↔ 视图对象。
 *
 * <p>出参一律为 VO（聚合不泄漏到适配层之外）；入参 {@link CouponCreateReq} → 领域聚合在此完成，
 * 业务归一化由聚合自身负责（请求对象只做校验绑定）。
 *
 * <p>UNMAPPED 忽略口径：{@code id}/{@code issuedQty}/{@code sellerId}/审计字段由基础设施回填。
 * coupon 的 {@code scopeType}/{@code scopeValues}/{@code stackRule}/{@code stackGroup} 四字段
 * 已与聚合同名同型（V4.3 范围与叠加维度落地），由 MapStruct 按名自动双向映射，无需显式
 * {@code @Mapping}。
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

    /**
     * 秒杀聚合 → 视图对象（仅本体字段）。
     *
     * <p>商品名/主图/原价需跨聚合回填、{@code progress_percent} 为派生值，二者由
     * {@code FlashSaleViewManager} 在装配后补齐；此前按列表批量映射的
     * {@code toFlashSaleVOList} 因回填改为逐元素装配已无调用方，随本批删除。
     */
    FlashSaleVO toVO(FlashSale flashSale);

    /** 秒杀创建请求 → 秒杀聚合（status/soldCount/version 由创建编排推导回填，AD40）。 */
    FlashSale toDomain(FlashSaleCreateReq req);

    /**
     * 秒杀编辑请求 → 就地合并到已取聚合（V5.7 AD41）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req       编辑请求（字段全可选）
     * @param flashSale 已取出的场次聚合（被合并目标）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void mergeFlashSaleUpdate(FlashSaleUpdateReq req, @MappingTarget FlashSale flashSale);

    /** 促销活动聚合 → 视图对象（AD48 创建回执口径）。 */
    PromotionVO toVO(Promotion promotion);

    /** 促销创建请求 → 促销聚合（status/version/maxDiscount 归一由创建编排负责，AD48）。 */
    Promotion toDomain(PromotionCreateReq req);

    /**
     * 促销编辑请求 → 就地合并到已取聚合（V5.8 AD49）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req       编辑请求（字段全可选）
     * @param promotion 已取出的活动聚合（被合并目标）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void mergePromotionUpdate(PromotionUpdateReq req, @MappingTarget Promotion promotion);

    /** 促销聚合 → 生效促销契约对象（I44，ia-api {@code PromotionCO}，ADR-043）。 */
    PromotionCO toCO(Promotion promotion);

    /** 促销聚合列表 → 生效促销契约对象列表（I44）。 */
    List<PromotionCO> toPromotionCOList(List<Promotion> promotions);

    /** 拼团创建请求 → 拼团活动聚合（status/version 由创建编排推导回填，AD54）。 */
    GroupBuyActivity toDomain(GroupBuyCreateReq req);

    /** 拼团活动聚合 → 视图对象（AD54 创建回执 / M15 列表 / M16 详情口径）。 */
    GroupBuyActivityVO toVO(GroupBuyActivity activity);

    /**
     * 拼团编辑请求 → 就地合并到已取聚合（V5.14 AD55）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req      编辑请求（字段全可选）
     * @param activity 已取出的活动聚合（被合并目标）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void mergeGroupBuyUpdate(GroupBuyUpdateReq req, @MappingTarget GroupBuyActivity activity);

    /** 拼团团聚合 → 团视图对象（M16 团列表 / M17 团进度；不出任何 userId）。 */
    GroupTeamVO toVO(GroupTeam team);

    /** 拼团团聚合列表 → 团视图对象列表（M16 详情页最近进行中团，≤10）。 */
    List<GroupTeamVO> toGroupTeamVOList(List<GroupTeam> teams);

    /** 拼团团聚合 → 团状态契约对象（I53 join 回执，ia-api {@code GroupTeamCO}）。 */
    GroupTeamCO toCO(GroupTeam team);

    /**
     * 促销聚合 → 活动中心视图对象（M12/M13，V5.11 契约定稿）。
     *
     * <p>{@code type} 需由数字编码映射为对外契约字符串（1满减→FULL_REDUCE / 2满折→LIMIT_DISCOUNT），
     * MapStruct 无法自动完成，故用 default 方法显式装配（同 {@link #toPointsBalanceVO} 先例）；
     * 装配逻辑统一收在适配层转换器中，控制器不手写转换。
     *
     * @param promotion 促销活动聚合
     * @return 活动视图对象（type 为契约字符串；类型未知时为 null，由查询用例保证不会出现）
     */
    default ActivityVO toActivityVO(Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        ActivityVO vo = new ActivityVO();
        vo.setId(promotion.getId());
        PromotionTypeEnum typeEnum = PromotionTypeEnum.of(promotion.getType());
        vo.setType(typeEnum != null ? typeEnum.getContractName() : null);
        vo.setName(promotion.getName());
        vo.setSellerId(promotion.getSellerId());
        vo.setThreshold(promotion.getThreshold());
        vo.setDiscountValue(promotion.getDiscountValue());
        vo.setMaxDiscount(promotion.getMaxDiscount());
        vo.setStartTime(promotion.getStartTime());
        vo.setEndTime(promotion.getEndTime());
        return vo;
    }

    /** 促销聚合列表 → 活动中心视图对象列表（M12；元素装配复用 {@link #toActivityVO}）。 */
    List<ActivityVO> toActivityVOList(List<Promotion> promotions);

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
