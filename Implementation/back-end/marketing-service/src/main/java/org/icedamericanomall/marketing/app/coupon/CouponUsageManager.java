package org.icedamericanomall.marketing.app.coupon;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.CouponStackInfoCO;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.domain.enums.CouponCategoryEnum;
import org.icedamericanomall.marketing.domain.enums.OrderTypeEnum;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.icedamericanomall.marketing.domain.service.CouponPricing;
import org.icedamericanomall.marketing.domain.service.CouponScope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优惠券核销用例族编排（应用层 · 下单核销/回滚/持券查询/叠加信息批量查询）。
 *
 * <p>折扣口径统一委托纯领域服务 {@link CouponPricing}；类别/店铺/适用范围适用性校验内聚在
 * {@link #useCoupon}（秒杀订单拒绝用券，店铺券限本店，{@link CouponScope} 限定品类/单品）。
 */
@Component
@RequiredArgsConstructor
public class CouponUsageManager {

    /** M7 三态计数契约键（05-API-Specification §4.10.1 M7，V5.11 定稿）。 */
    private static final String COUNT_KEY_AVAILABLE = "available";
    private static final String COUNT_KEY_USED = "used";
    private static final String COUNT_KEY_EXPIRED = "expired";

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final MarketingConverter marketingConverter;

    /** 未使用券列表（向后兼容口径）。 */
    public List<UserCouponVO> listAvailableCoupons(Long userId) {
        return marketingConverter.toUserCouponVOList(userCouponRepository.findUnusedByUserId(userId));
    }

    /** 已使用/已过期券列表（按使用时间倒序）。 */
    public List<UserCouponVO> listUsedCoupons(Long userId) {
        return marketingConverter.toUserCouponVOList(userCouponRepository.findUsedByUserId(userId));
    }

    /**
     * 我的券三态计数（M7，V5.11 契约定稿）。
     *
     * <p>状态口径与列表查询一致（{@code user_coupon.status} 落库态，{@link UserCouponStatusEnum}）：
     * {@code available}=未使用（1）/ {@code used}=已使用（2）/ {@code expired}=已过期（3）。
     *
     * @param userId 当前登录用户 ID
     * @return 三态计数（键固定 available/used/expired，值非负）
     */
    public Map<String, Integer> countMyCoupons(Long userId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(COUNT_KEY_AVAILABLE, toIntCount(userId, UserCouponStatusEnum.UNUSED));
        counts.put(COUNT_KEY_USED, toIntCount(userId, UserCouponStatusEnum.USED));
        counts.put(COUNT_KEY_EXPIRED, toIntCount(userId, UserCouponStatusEnum.EXPIRED));
        return counts;
    }

    /** 单状态计数（count 返回 long，契约为 Integer）。 */
    private int toIntCount(Long userId, UserCouponStatusEnum status) {
        return (int) userCouponRepository.countByUserIdAndStatus(userId, status);
    }

    /**
     * 使用优惠券（下单时调用），支持 discountType 三维度、couponCategory 适用范围与
     * {@code scopeType/scopeValues} 适用对象三重校验。
     *
     * <p><b>类别规则</b>：PLATFORM 通用；SHOP 限本店订单；FLASH_SALE 券仅限秒杀订单；而秒杀订单
     * （{@code orderType=FLASH_SALE}）本身拒绝所有优惠券（秒杀价已是底价，后端兜底）。
     *
     * <p><b>适用对象规则</b>（{@link CouponScope}，与 {@code coupon.scope_type} 同口径）：
     * <ul>
     *   <li>{@code scopeType=1 ALL}、或 {@code scopeType=null}、或 {@code scopeValues} 为空
     *       → 不分商品/品类，放行（存量数据向后兼容）；</li>
     *   <li>{@code scopeType=2 CATEGORY} → 订单品类需与 {@code scopeValues} 有交集；</li>
     *   <li>{@code scopeType=3 PRODUCT} → 订单商品需与 {@code scopeValues} 有交集；</li>
     *   <li>无交集（<b>含订单侧未传 ids</b>）→ 抛 {@code BizException}「不适用于当前订单商品」。
     *       未传 ids 不放行：调用方漏传不能被解释为「用户想要的范围被满足」。</li>
     * </ul>
     *
     * @param productIds  订单商品 ID（逗号分隔；调用方来自下单商品快照，未传=未知）
     * @param categoryIds 订单品类 ID（逗号分隔；同上）
     * @return 实际抵扣金额（分）
     */
    @Transactional(rollbackFor = Exception.class)
    public int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount,
                         Integer orderType, Long sellerId, String productIds, String categoryIds) {
        UserCoupon uc = userCouponRepository.findById(userCouponId);
        if (uc == null || !uc.isOwnedBy(userId)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不存在");
        }
        if (!uc.isUnused()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不可用");
        }

        Coupon coupon = couponRepository.findById(uc.getCouponId());
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND, "优惠券模板已删除");
        }
        if (orderAmount < coupon.getMinAmount()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未达到最低消费金额");
        }
        assertCategoryUsable(coupon, orderType, sellerId);
        if (!CouponScope.accepts(coupon.getScopeType(), coupon.getScopeValues(), productIds, categoryIds)) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券不适用于当前订单商品");
        }

        int discountType = CouponPricing.resolveDiscountType(coupon.getDiscountType(), coupon.getType());
        int discount = CouponPricing.calculate(discountType, coupon.getValue(),
                coupon.getMinAmount(), orderAmount);

        uc.markUsed(orderNo);
        userCouponRepository.update(uc);
        return discount;
    }

    /** 券类别适用性校验（PLATFORM 通用 / SHOP 限本店 / FLASH_SALE 仅秒杀；秒杀订单拒用一切券）。 */
    private void assertCategoryUsable(Coupon coupon, Integer orderType, Long sellerId) {
        int category = coupon.getCouponCategory() != null ? coupon.getCouponCategory()
                : (coupon.getSellerId() == null ? CouponCategoryEnum.PLATFORM.getCode()
                                                : CouponCategoryEnum.SHOP.getCode());
        if (orderType != null) {
            if (orderType == OrderTypeEnum.FLASH_SALE.getCode()) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀订单不支持使用优惠券");
            }
            // FLASH_SALE 券仅限秒杀订单 → 但秒杀订单已在上方拒绝，此处为语义完整性
            if (category == CouponCategoryEnum.FLASH_SALE.getCode()) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀券仅限秒杀订单使用");
            }
        }
        if (category == CouponCategoryEnum.SHOP.getCode()) {
            if (coupon.getSellerId() == null || !coupon.getSellerId().equals(sellerId)) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "店铺券仅限该店铺订单使用");
            }
        }
    }

    /**
     * 批量查询持券的叠加信息（I46 {@code GET /internal/coupon/batch}，供 trade-service
     * 下单时校验多券叠加规则）。
     *
     * <p><b>入参口径</b>：{@code userCouponIds} 是 {@code user_coupon.id}（与
     * {@link #useCoupon} 的 {@code userCouponId} 同口径，调用方 {@code PricingService.applyCoupons}
     * 即传该值）；因此返回的 {@link CouponStackInfoCO#getId()} 填 {@code user_coupon.id}，
     * 便于调用方按 id 与原入参对应；{@code couponId} 填券业务标识（模板 hex 券号）。
     *
     * <p><b>fail-open 语义</b>：入参为空、券不存在（模板已删）时<b>跳过</b>而非抛异常 ——
     * 返回值被调用方解释为「叠加约束事实」，缺项只意味着该券不参与约束判定，不能让查询失败
     * 阻断下单（ADR-011 降级三分法，与 {@code CouponClientFallback} 口径一致）。
     *
     * <p>为避免 N+1，持券按 ID 列表一次查出（{@link UserCouponRepository#findByIds})。
     *
     * @param userCouponIds 持券 ID 列表（可为 {@code null}/空）
     * @return 叠加信息列表（跳过无对应模板的持券；<b>顺序不保证</b>，调用方按 {@code id} 对应）
     */
    public List<CouponStackInfoCO> batchStackInfo(List<Long> userCouponIds) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return List.of();
        }
        List<UserCoupon> userCoupons = userCouponRepository.findByIds(userCouponIds);
        if (userCoupons == null || userCoupons.isEmpty()) {
            return List.of();
        }
        List<CouponStackInfoCO> infos = new ArrayList<>(userCoupons.size());
        for (UserCoupon uc : userCoupons) {
            Coupon coupon = couponRepository.findById(uc.getCouponId());
            if (coupon == null) {
                continue; // 模板已删除：跳过而非报错（fail-open）
            }
            infos.add(toStackInfo(uc, coupon));
        }
        return infos;
    }

    /** 持券 + 券模板 → 叠加信息契约对象（模板字段原样透出，不做业务判定）。 */
    private static CouponStackInfoCO toStackInfo(UserCoupon uc, Coupon coupon) {
        CouponStackInfoCO info = new CouponStackInfoCO();
        info.setId(uc.getId());
        info.setCouponId(coupon.getCouponId());
        info.setCouponCategory(coupon.getCouponCategory());
        info.setStackRule(coupon.getStackRule());
        info.setStackGroup(coupon.getStackGroup());
        info.setDiscountType(coupon.getDiscountType());
        info.setSellerId(coupon.getSellerId());
        return info;
    }

    /** 按持券 ID 回滚（已使用才回滚，幂等）。 */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long userCouponId) {
        UserCoupon uc = userCouponRepository.findById(userCouponId);
        if (uc != null && uc.isUsed()) {
            restore(uc);
        }
    }

    /** 按订单号回滚优惠券（订单取消/超时补偿）。 */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackByOrderNo(String orderNo) {
        UserCoupon uc = userCouponRepository.findUsedByOrderNo(orderNo);
        if (uc != null) {
            restore(uc);
        }
    }

    /** 回滚一张已使用的券为可用。 */
    private void restore(UserCoupon uc) {
        uc.restore();
        userCouponRepository.update(uc);
    }
}
