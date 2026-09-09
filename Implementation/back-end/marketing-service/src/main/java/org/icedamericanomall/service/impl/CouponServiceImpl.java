package org.icedamericanomall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.constants.*;
import org.icedamericanomall.domain.dto.CouponAvailableFilterReq;
import org.icedamericanomall.domain.entity.CouponEntity;
import org.icedamericanomall.domain.entity.UserCouponEntity;
import org.icedamericanomall.domain.vo.CouponAvailableVO;
import org.icedamericanomall.mapper.CouponMapper;
import org.icedamericanomall.mapper.UserCouponMapper;
import org.icedamericanomall.service.CouponService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * V4.3: 多维度优惠券服务实现（京东标准）。
 * 新增: 适用范围(scopeType×scopeValues) + 预过滤 + 多券叠加。
 */
@Slf4j
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, CouponEntity> implements CouponService {

    private final UserCouponMapper userCouponMapper;
    private final CouponGrabLuaScript couponGrabLuaScript;

    public CouponServiceImpl(UserCouponMapper userCouponMapper, CouponGrabLuaScript couponGrabLuaScript) {
        this.userCouponMapper = userCouponMapper;
        this.couponGrabLuaScript = couponGrabLuaScript;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity claim(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        if (LocalDateTime.now().isAfter(coupon.getEndTime()))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已过期");

        if (coupon.getGrabType() != null && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode()) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION,
                    "该优惠券为秒杀券，请使用 /api/coupon/grab 接口领取");
        }

        Long count = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (count > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已领取过该优惠券");

        if (coupon.getStockType() == null || coupon.getStockType() == CouponStockTypeEnum.LIMITED.getCode()) {
            boolean incremented = lambdaUpdate().eq(CouponEntity::getId, coupon.getId())
                    .lt(CouponEntity::getIssuedQty, coupon.getTotalQty())
                    .setSql("issued_qty = issued_qty + 1").update();
            if (!incremented) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已领完");
        }

        if (coupon.getGrantType() != null && coupon.getGrantType() == GrantTypeEnum.PAID_PURCHASE.getCode()) {
            UserCouponEntity prePaid = userCouponMapper.selectOne(
                    new LambdaQueryWrapper<UserCouponEntity>()
                            .eq(UserCouponEntity::getUserId, userId)
                            .eq(UserCouponEntity::getCouponId, coupon.getId())
                            .eq(UserCouponEntity::getStatus, 4));
            if (prePaid == null)
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "请先购买该优惠券");
            prePaid.setStatus(1);
            userCouponMapper.updateById(prePaid);
            return prePaid;
        }

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(1);
        userCouponMapper.insert(uc);
        return uc;
    }

    @Override
    public List<UserCouponEntity> getUserAvailableCoupons(Long userId) {
        return userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getStatus, 1));
    }

    @Override
    public List<UserCouponEntity> getUserUsedCoupons(Long userId) {
        return userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .ne(UserCouponEntity::getStatus, 1)
                        .orderByDesc(UserCouponEntity::getUseTime));
    }

    // === V4.3: useCoupon — 完整校验链 (couponCategory + sellerId + scopeType) ===

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int useCoupon(Long userId, Long userCouponId, String orderNo, int orderAmount,
                          Integer orderType, Long sellerId, String productIds, String categoryIds) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId))
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不存在");
        if (uc.getStatus() != 1)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不可用");

        CouponEntity coupon = getById(uc.getCouponId());
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券模板已删除");
        if (orderAmount < coupon.getMinAmount())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未达到最低消费金额");

        // V4.2: couponCategory × orderType/sellerId
        int category = coupon.getCouponCategory() != null ? coupon.getCouponCategory()
                : (coupon.getSellerId() == null ? CouponCategoryEnum.PLATFORM.getCode()
                                                 : CouponCategoryEnum.SHOP.getCode());
        if (orderType != null) {
            if (orderType == 3) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀订单不支持使用优惠券");
            }
            if (category == CouponCategoryEnum.FLASH_SALE.getCode() && orderType != 3) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀券仅限秒杀订单使用");
            }
        }
        if (category == CouponCategoryEnum.SHOP.getCode()) {
            if (coupon.getSellerId() == null || !coupon.getSellerId().equals(sellerId)) {
                throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "店铺券仅限该店铺订单使用");
            }
        }

        // V4.3: scopeType × 商品/品类匹配
        int scope = coupon.getScopeType() != null ? coupon.getScopeType() : ScopeTypeEnum.ALL.getCode();
        if (scope != ScopeTypeEnum.ALL.getCode()) {
            if (scope == ScopeTypeEnum.CATEGORY.getCode()) {
                List<Long> scopeCatIds = parseJsonLongList(coupon.getScopeValues());
                List<Long> orderCatIds = parseCommaSepLongs(categoryIds);
                if (orderCatIds.isEmpty()
                        || scopeCatIds.stream().noneMatch(orderCatIds::contains)) {
                    throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不适用于该商品品类");
                }
            } else if (scope == ScopeTypeEnum.PRODUCT.getCode()) {
                List<Long> scopeProductIds = parseJsonLongList(coupon.getScopeValues());
                List<Long> orderProductIds = parseCommaSepLongs(productIds);
                if (orderProductIds.isEmpty()
                        || scopeProductIds.stream().noneMatch(orderProductIds::contains)) {
                    throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券不适用于该商品");
                }
            }
        }

        // 折扣计算
        int dt = coupon.getDiscountType() != null ? coupon.getDiscountType() : coupon.getType();
        int discount;
        if (dt == DiscountTypeEnum.FIXED.getCode()) {
            discount = coupon.getValue();
        } else if (dt == DiscountTypeEnum.PERCENTAGE.getCode()) {
            discount = orderAmount * (100 - coupon.getValue()) / 100;
        } else if (dt == DiscountTypeEnum.CASH_COUPON.getCode()) {
            discount = Math.min(coupon.getValue(), orderAmount);
        } else {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "未知的优惠券类型");
        }
        discount = Math.min(discount, orderAmount);

        uc.setStatus(2);
        uc.setUsedOrderNo(orderNo);
        uc.setUseTime(LocalDateTime.now());
        userCouponMapper.updateById(uc);

        return discount;
    }

    // === V4.3: 预过滤——结算页可用券列表 ===

    @Override
    public List<CouponAvailableVO> getAvailableCoupons(Long userId, CouponAvailableFilterReq filter) {
        List<UserCouponEntity> userCoupons = getUserAvailableCoupons(userId);
        if (userCoupons.isEmpty()) return List.of();

        List<CouponAvailableVO> result = new ArrayList<>();
        for (UserCouponEntity uc : userCoupons) {
            CouponEntity coupon = getById(uc.getCouponId());
            if (coupon == null || coupon.getStatus() != 1) continue;

            CouponAvailableVO vo = new CouponAvailableVO();
            vo.setUserCouponId(uc.getId());
            vo.setCouponId(coupon.getCouponId());
            vo.setName(coupon.getName());
            vo.setDiscountType(coupon.getDiscountType());
            vo.setValue(coupon.getValue());
            vo.setMinAmount(coupon.getMinAmount());
            vo.setCouponCategory(coupon.getCouponCategory());
            vo.setScopeType(coupon.getScopeType());
            vo.setEndTime(coupon.getEndTime());

            String reason = checkApplicability(coupon, filter);
            vo.setApplicable(reason == null);
            vo.setUnapplicableReason(reason);

            if (reason == null) {
                // 预估折扣（仅参考，实际以下单时计算为准）
                vo.setEstimatedDiscount(estimateDiscount(coupon, filter.getTotalAmount()));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<CouponEntity> getBatchByIds(List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) return List.of();
        return listByIds(couponIds);
    }

    // === private helpers ===

    /** 检查券是否适用于订单上下文，返回不可用原因（null=适用） */
    private String checkApplicability(CouponEntity coupon, CouponAvailableFilterReq filter) {
        // 过期
        if (LocalDateTime.now().isAfter(coupon.getEndTime())) return "已过期";

        // 秒杀订单不用券
        if (filter.getOrderType() != null && filter.getOrderType() == 3)
            return "秒杀订单不支持优惠券";
        if (coupon.getCouponCategory() != null
                && coupon.getCouponCategory() == CouponCategoryEnum.FLASH_SALE.getCode()
                && filter.getOrderType() != null && filter.getOrderType() != 3)
            return "秒杀券仅限秒杀订单使用";

        // 店铺券 × 卖家匹配
        if (coupon.getCouponCategory() != null
                && coupon.getCouponCategory() == CouponCategoryEnum.SHOP.getCode()) {
            if (coupon.getSellerId() != null && !coupon.getSellerId().equals(filter.getSellerId()))
                return "仅限指定店铺使用";
        }

        // 最低消费
        if (filter.getTotalAmount() != null && coupon.getMinAmount() != null
                && filter.getTotalAmount() < coupon.getMinAmount()) {
            return "未达最低消费" + (coupon.getMinAmount() / 100) + "元";
        }

        // V4.3: scopeType 品类匹配
        int scope = coupon.getScopeType() != null ? coupon.getScopeType() : ScopeTypeEnum.ALL.getCode();
        if (scope == ScopeTypeEnum.CATEGORY.getCode()) {
            List<Long> scopeCats = parseJsonLongList(coupon.getScopeValues());
            List<Long> orderCats = filter.getCategoryIds();
            if (orderCats == null || orderCats.isEmpty()
                    || scopeCats.stream().noneMatch(orderCats::contains)) {
                return "仅限指定品类";
            }
        }
        if (scope == ScopeTypeEnum.PRODUCT.getCode()) {
            List<Long> scopeProds = parseJsonLongList(coupon.getScopeValues());
            // 单品匹配需要 productIds，通过 skuIds→productId 查询后使用
            // 前端传入 categoryIds 包含了对应的品类信息，如果需要精确匹配 product 需要额外查询
            if (filter.getSkuIds() == null || filter.getSkuIds().isEmpty()) return "仅限指定商品";
            // 简化：如果前端传了 productIds（放在 categoryIds 复用），此处做包含检查
            // 完整方案需要新增 productIds 参数到 filter
        }

        return null; // 适用
    }

    /** 预估折扣金额 */
    private int estimateDiscount(CouponEntity coupon, Integer totalAmount) {
        if (totalAmount == null) return 0;
        int dt = coupon.getDiscountType() != null ? coupon.getDiscountType() : coupon.getType();
        if (dt == DiscountTypeEnum.FIXED.getCode()) return Math.min(coupon.getValue(), totalAmount);
        if (dt == DiscountTypeEnum.PERCENTAGE.getCode())
            return Math.min(totalAmount * (100 - coupon.getValue()) / 100, totalAmount);
        if (dt == DiscountTypeEnum.CASH_COUPON.getCode()) return Math.min(coupon.getValue(), totalAmount);
        return 0;
    }

    private List<Long> parseJsonLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSONUtil.toList(json, Long.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Long> parseCommaSepLongs(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            List<Long> result = new ArrayList<>();
            for (String part : csv.split(",")) {
                result.add(Long.parseLong(part.trim()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    // === V4.0-4.2 helper methods (unchanged) ===

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long userCouponId) {
        UserCouponEntity uc = userCouponMapper.selectById(userCouponId);
        if (uc != null && uc.getStatus() == 2) {
            uc.setStatus(1);
            uc.setUsedOrderNo(null);
            uc.setUseTime(null);
            userCouponMapper.updateById(uc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackByOrderNo(String orderNo) {
        UserCouponEntity uc = userCouponMapper.selectOne(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUsedOrderNo, orderNo)
                        .eq(UserCouponEntity::getStatus, 2));
        if (uc != null) {
            uc.setStatus(1);
            uc.setUsedOrderNo(null);
            uc.setUseTime(null);
            userCouponMapper.updateById(uc);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity purchaseCoupon(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getGrantType() == null || coupon.getGrantType() != GrantTypeEnum.PAID_PURCHASE.getCode())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券无需购买，可直接领取");
        if (coupon.getPriceInCents() == null || coupon.getPriceInCents() <= 0)
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券价格未配置");

        Long exists = userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCouponEntity>()
                        .eq(UserCouponEntity::getUserId, userId)
                        .eq(UserCouponEntity::getCouponId, coupon.getId()));
        if (exists > 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已购买或领取过该优惠券");

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(4);
        userCouponMapper.insert(uc);
        return uc;
    }

    @Override
    public UserCouponEntity claimWithGrab(Long userId, String couponId) {
        CouponEntity coupon = lambdaQuery().eq(CouponEntity::getCouponId, couponId).one();
        if (coupon == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "优惠券不存在");
        if (coupon.getStatus() != 1) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "优惠券已失效");
        if (coupon.getGrabType() == null || coupon.getGrabType() != GrabTypeEnum.NEED_GRAB.getCode())
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "该优惠券无需抢，请使用普通领取接口");

        long result = couponGrabLuaScript.tryClaim(coupon.getId(), userId);
        if (result == -1) throw new BizException(ErrorCode.FLASH_SALE_SOLD_OUT, "优惠券已抢光");
        if (result == -2) throw new BizException(ErrorCode.FLASH_SALE_LIMIT_EXCEEDED, "已领取过该优惠券");

        UserCouponEntity uc = new UserCouponEntity();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        uc.setStatus(1);
        userCouponMapper.insert(uc);

        try {
            lambdaUpdate().eq(CouponEntity::getId, coupon.getId())
                    .setIncrBy(CouponEntity::getIssuedQty, 1).update();
        } catch (Exception e) {
            log.error("秒杀券 issued_qty 同步失败（非关键）: couponId={}", coupon.getId(), e);
        }

        return uc;
    }
}
