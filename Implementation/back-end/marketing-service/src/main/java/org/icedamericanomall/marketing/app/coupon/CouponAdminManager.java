package org.icedamericanomall.marketing.app.coupon;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.domain.enums.CouponCategoryEnum;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.enums.GrabTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.port.CouponGrabStore;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.infrastructure.constant.MarketingRedisKeys;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 优惠券管理用例族编排（应用层 · 平台后台 + 商家店铺券）。
 *
 * <p>查询与状态流转在本层完成（控制器不直接拼查询条件或调 Redis 预热）；
 * NEED_GRAB 券在创建时自动预热 Redis 库存，TTL 与活动结束时间对齐。
 */
@Component
@RequiredArgsConstructor
public class CouponAdminManager {

    /** 下线状态：草稿（模板列表按 {@code status=ACTIVE} 过滤，故下线后不可再领取）。 */
    private static final int STATUS_DISABLED = CouponStatusEnum.DRAFT.getCode();

    private final CouponRepository couponRepository;
    private final CouponGrabStore couponGrabStore;
    private final MarketingConverter marketingConverter;

    /** 平台后台创建优惠券模板（couponId 可省略，规则见 {@link #normalizeCouponId}）。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponVO createCoupon(CouponCreateReq req) {
        return create(marketingConverter.toDomain(req));
    }

    /** 创建店铺优惠券（归属参数指定商家，缺省类别 SHOP）。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponVO createShopCoupon(Long sellerId, CouponCreateReq req) {
        Coupon coupon = marketingConverter.toDomain(req);
        coupon.setSellerId(sellerId);
        if (coupon.getCouponCategory() == null) {
            coupon.setCouponCategory(CouponCategoryEnum.SHOP.getCode());
        }
        return create(coupon);
    }

    /** 可领取模板列表：已发布且未到期。 */
    public List<CouponVO> listActiveTemplates() {
        return marketingConverter.toCouponVOList(
                couponRepository.findActiveTemplates(LocalDateTime.now()));
    }

    /** 商家店铺券列表（创建时间倒序）。 */
    public List<CouponVO> listShopCoupons(Long sellerId) {
        return marketingConverter.toCouponVOList(couponRepository.findBySeller(sellerId));
    }

    /** 下线平台券（幂等：已下线重复调用同样返回当前状态）。 */
    @Transactional(rollbackFor = Exception.class)
    public CouponVO disableCoupon(String couponId) {
        Coupon coupon = couponRepository.findByCouponId(couponId);
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND);
        }
        return marketingConverter.toVO(disable(coupon));
    }

    /**
     * 下线本店券（幂等）。
     *
     * <p>非本店券按「不存在」处理：不泄露他人券的存在性（越权防范）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponVO disableShopCoupon(String couponId, Long sellerId) {
        Coupon coupon = couponRepository.findByCouponIdAndSeller(couponId, sellerId);
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_NOT_FOUND);
        }
        return marketingConverter.toVO(disable(coupon));
    }

    // ==================== 内部方法 ====================

    /** 新增优惠券并预热秒杀库存。 */
    private CouponVO create(Coupon coupon) {
        coupon.setCouponId(normalizeCouponId(coupon.getCouponId()));
        coupon.setIssuedQty(0);
        couponRepository.save(coupon);
        preloadGrabStockIfNeeded(coupon);
        return marketingConverter.toVO(coupon);
    }

    /**
     * 券业务码归一（ADR-050 契约收紧）：
     * <ul>
       <li>空 → 系统生成 UUIDv7（32 位小写 hex）；</li>
       <li>已提供 → 必须为 32 位 hex 或 36 位连字符 UUID，统一归一为 32 位小写 hex 落库
           （BINARY(16)，仓储边界转换）；</li>
       <li>非法输入（如 {@code CPN-001} 这类可读编码）→ {@code PARAM_ERROR} fail-fast
           ——列已收紧为 BINARY(16)，不可读编码不再可存。</li>
     * </ul>
     */
    static String normalizeCouponId(String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return IdGenerator.nextId();
        }
        try {
            return IdGenerator.toHexString(IdGenerator.toBytes(couponId));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "couponId 必须为 32 位 hex 或 36 位连字符 UUID（可省略，由系统生成）: " + couponId);
        }
    }

    /** 幂等下线：已是下线状态直接返回，不再写库。 */
    private Coupon disable(Coupon coupon) {
        if (!Objects.equals(coupon.getStatus(), STATUS_DISABLED)) {
            coupon.setStatus(STATUS_DISABLED);
            couponRepository.update(coupon);
        }
        return coupon;
    }

    /** NEED_GRAB 券创建后自动预热 Redis 库存（券不预热则永远无库存可抢）。 */
    private void preloadGrabStockIfNeeded(Coupon coupon) {
        boolean needGrab = coupon.getGrabType() != null
                && coupon.getGrabType() == GrabTypeEnum.NEED_GRAB.getCode();
        if (!needGrab || coupon.getTotalQty() == null || coupon.getTotalQty() <= 0) {
            return;
        }
        couponGrabStore.preloadStock(coupon.getId(), coupon.getTotalQty(),
                MarketingRedisKeys.ttlUntil(coupon.getEndTime()));
    }
}
