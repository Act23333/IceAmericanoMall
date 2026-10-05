package org.icedamericanomall.marketing.infrastructure.persistence.promotion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.repository.PromotionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 促销活动仓储实现 —— PO 与聚合互转在本类完成（内联 static toPO/toDomain）。
 *
 * <p>I44「进行中」口径按时间窗推导（{@code start_time ≤ now ≤ end_time}），
 * 不叠加落库 {@code status} 快照条件（快照可能停留在创建时刻的「未开始」态，
 * 时间窗才是状态权威口径，ADR-043）；命中索引 {@code idx_seller_time}。
 */
@Repository
@RequiredArgsConstructor
public class PromotionRepositoryImpl implements PromotionRepository {

    private final PromotionMapper promotionMapper;

    @Override
    public Promotion findById(Long id) {
        return toDomain(promotionMapper.selectById(id));
    }

    @Override
    public List<Promotion> findActiveBySeller(Long sellerId, LocalDateTime now) {
        return promotionMapper.selectList(new LambdaQueryWrapper<PromotionPO>()
                        .eq(PromotionPO::getSellerId, sellerId)
                        .le(PromotionPO::getStartTime, now)
                        .ge(PromotionPO::getEndTime, now)
                        .orderByAsc(PromotionPO::getId))
                .stream().map(PromotionRepositoryImpl::toDomain).toList();
    }

    @Override
    public void save(Promotion promotion) {
        PromotionPO po = toPO(promotion);
        promotionMapper.insert(po);
        promotion.setId(po.getId());
    }

    @Override
    public void update(Promotion promotion) {
        promotionMapper.updateById(toPO(promotion));
    }

    @Override
    public void deleteById(Long id) {
        promotionMapper.deleteById(id);
    }

    // ==================== PO <-> 聚合 ====================

    static Promotion toDomain(PromotionPO po) {
        if (po == null) {
            return null;
        }
        return Promotion.builder()
                .id(po.getId())
                .name(po.getName())
                .sellerId(po.getSellerId())
                .type(po.getType())
                .threshold(po.getThreshold())
                .discountValue(po.getDiscountValue())
                .maxDiscount(po.getMaxDiscount())
                .startTime(po.getStartTime())
                .endTime(po.getEndTime())
                .status(po.getStatus())
                .version(po.getVersion())
                .createTime(po.getCreateTime())
                .build();
    }

    static PromotionPO toPO(Promotion promotion) {
        PromotionPO po = new PromotionPO();
        po.setId(promotion.getId());
        po.setName(promotion.getName());
        po.setSellerId(promotion.getSellerId());
        po.setType(promotion.getType());
        po.setThreshold(promotion.getThreshold());
        po.setDiscountValue(promotion.getDiscountValue());
        po.setMaxDiscount(promotion.getMaxDiscount());
        po.setStartTime(promotion.getStartTime());
        po.setEndTime(promotion.getEndTime());
        po.setStatus(promotion.getStatus());
        po.setVersion(promotion.getVersion());
        po.setCreateTime(promotion.getCreateTime());
        return po;
    }
}
