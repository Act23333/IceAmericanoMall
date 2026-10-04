package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.enums.CouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券模板仓储实现 —— PO 与聚合互转在本类完成，外部只见领域对象。
 *
 * <p>库存扣减不用「先查后写」：{@code issued_qty &lt; total_qty} 的条件 UPDATE 由数据库保证原子，
 * 影响行数 0 即「已领完」（与 Redis 预扣互为双保险）。
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponMapper couponMapper;

    @Override
    public Coupon findByCouponId(String couponId) {
        return toDomain(couponMapper.selectByCouponId(couponId));
    }

    @Override
    public Coupon findByCouponIdAndSeller(String couponId, Long sellerId) {
        return toDomain(couponMapper.selectOne(new LambdaQueryWrapper<CouponPO>()
                .eq(CouponPO::getCouponId, couponId)
                .eq(CouponPO::getSellerId, sellerId)));
    }

    @Override
    public Coupon findById(Long id) {
        return toDomain(couponMapper.selectById(id));
    }

    @Override
    public Coupon save(Coupon coupon) {
        CouponPO po = toPO(coupon);
        couponMapper.insert(po);
        coupon.setId(po.getId());
        return coupon;
    }

    @Override
    public void update(Coupon coupon) {
        couponMapper.updateById(toPO(coupon));
    }

    @Override
    public boolean incrementIssuedQtyIfBelowTotal(Long id, int totalQty) {
        return couponMapper.update(null, new LambdaUpdateWrapper<CouponPO>()
                .eq(CouponPO::getId, id)
                .lt(CouponPO::getIssuedQty, totalQty)
                .setSql("issued_qty = issued_qty + 1")) > 0;
    }

    @Override
    public void incrementIssuedQty(Long id) {
        couponMapper.update(null, new LambdaUpdateWrapper<CouponPO>()
                .eq(CouponPO::getId, id)
                .setSql("issued_qty = issued_qty + 1"));
    }

    @Override
    public List<Coupon> findActiveTemplates(LocalDateTime now) {
        return couponMapper.selectList(new LambdaQueryWrapper<CouponPO>()
                        .eq(CouponPO::getStatus, CouponStatusEnum.ACTIVE.getCode())
                        .gt(CouponPO::getEndTime, now))
                .stream().map(CouponRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<Coupon> findBySeller(Long sellerId) {
        return couponMapper.selectList(new LambdaQueryWrapper<CouponPO>()
                        .eq(CouponPO::getSellerId, sellerId)
                        .orderByDesc(CouponPO::getCreateTime))
                .stream().map(CouponRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    static Coupon toDomain(CouponPO po) {
        if (po == null) {
            return null;
        }
        return Coupon.builder()
                .id(po.getId())
                .couponId(po.getCouponId())
                .name(po.getName())
                .type(po.getType())
                .value(po.getValue())
                .minAmount(po.getMinAmount())
                .totalQty(po.getTotalQty())
                .issuedQty(po.getIssuedQty())
                .startTime(po.getStartTime())
                .endTime(po.getEndTime())
                .status(po.getStatus())
                .sellerId(po.getSellerId())
                .discountType(po.getDiscountType())
                .couponCategory(po.getCouponCategory())
                .grantType(po.getGrantType())
                .stockType(po.getStockType())
                .grabType(po.getGrabType())
                .priceInCents(po.getPriceInCents())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    static CouponPO toPO(Coupon coupon) {
        CouponPO po = new CouponPO();
        po.setId(coupon.getId());
        po.setCouponId(coupon.getCouponId());
        po.setName(coupon.getName());
        po.setType(coupon.getType());
        po.setValue(coupon.getValue());
        po.setMinAmount(coupon.getMinAmount());
        po.setTotalQty(coupon.getTotalQty());
        po.setIssuedQty(coupon.getIssuedQty());
        po.setStartTime(coupon.getStartTime());
        po.setEndTime(coupon.getEndTime());
        po.setStatus(coupon.getStatus());
        po.setSellerId(coupon.getSellerId());
        po.setDiscountType(coupon.getDiscountType());
        po.setCouponCategory(coupon.getCouponCategory());
        po.setGrantType(coupon.getGrantType());
        po.setStockType(coupon.getStockType());
        po.setGrabType(coupon.getGrabType());
        po.setPriceInCents(coupon.getPriceInCents());
        po.setCreateTime(coupon.getCreateTime());
        po.setUpdateTime(coupon.getUpdateTime());
        return po;
    }
}
