package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.UserCouponRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户持券仓储实现 —— PO 与聚合互转在本类完成。
 */
@Repository
@RequiredArgsConstructor
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponMapper userCouponMapper;

    @Override
    public UserCoupon findById(Long id) {
        return toDomain(userCouponMapper.selectById(id));
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        UserCouponPO po = toPO(userCoupon);
        userCouponMapper.insert(po);
        userCoupon.setId(po.getId());
        return userCoupon;
    }

    @Override
    public void update(UserCoupon userCoupon) {
        userCouponMapper.updateById(toPO(userCoupon));
    }

    @Override
    public long countByUserIdAndCouponId(Long userId, Long couponId) {
        return userCouponMapper.selectCount(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getCouponId, couponId));
    }

    @Override
    public List<UserCoupon> findUnusedByUserId(Long userId) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCouponPO>()
                        .eq(UserCouponPO::getUserId, userId)
                        .eq(UserCouponPO::getStatus, UserCouponStatusEnum.UNUSED.getCode()))
                .stream().map(UserCouponRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<UserCoupon> findUsedByUserId(Long userId) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCouponPO>()
                        .eq(UserCouponPO::getUserId, userId)
                        .ne(UserCouponPO::getStatus, UserCouponStatusEnum.UNUSED.getCode())
                        .orderByDesc(UserCouponPO::getUseTime))
                .stream().map(UserCouponRepositoryImpl::toDomain).toList();
    }

    @Override
    public UserCoupon findUsedByOrderNo(String orderNo) {
        return toDomain(userCouponMapper.selectOne(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUsedOrderNo, orderNo)
                .eq(UserCouponPO::getStatus, UserCouponStatusEnum.USED.getCode())));
    }

    @Override
    public long countByUserIdAndStatus(Long userId, UserCouponStatusEnum status) {
        return userCouponMapper.selectCount(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getStatus, status.getCode()));
    }

    // ==================== PO <-> 聚合 ====================

    static UserCoupon toDomain(UserCouponPO po) {
        if (po == null) {
            return null;
        }
        return UserCoupon.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .couponId(po.getCouponId())
                .status(po.getStatus())
                .usedOrderNo(po.getUsedOrderNo())
                .useTime(po.getUseTime())
                .createTime(po.getCreateTime())
                .build();
    }

    static UserCouponPO toPO(UserCoupon userCoupon) {
        UserCouponPO po = new UserCouponPO();
        po.setId(userCoupon.getId());
        po.setUserId(userCoupon.getUserId());
        po.setCouponId(userCoupon.getCouponId());
        po.setStatus(userCoupon.getStatus());
        po.setUsedOrderNo(userCoupon.getUsedOrderNo());
        po.setUseTime(userCoupon.getUseTime());
        po.setCreateTime(userCoupon.getCreateTime());
        return po;
    }
}
