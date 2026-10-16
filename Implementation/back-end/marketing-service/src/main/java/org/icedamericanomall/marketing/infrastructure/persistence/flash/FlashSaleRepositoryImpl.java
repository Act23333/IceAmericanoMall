package org.icedamericanomall.marketing.infrastructure.persistence.flash;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.enums.FlashSaleStatusEnum;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.repository.FlashSaleRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀场次仓储实现 —— PO 与聚合互转在本类完成。
 *
 * <p>异步统计的条件更新（版本号 + 库存守卫）下沉在此：消费者重试语义不变，
 * 冲突即返回 false 由调用方告警/重试。
 */
@Repository
@RequiredArgsConstructor
public class FlashSaleRepositoryImpl implements FlashSaleRepository {

    private final FlashSaleMapper flashSaleMapper;

    @Override
    public FlashSale findById(Long id) {
        return toDomain(flashSaleMapper.selectById(id));
    }

    @Override
    public List<FlashSale> findActive(LocalDateTime now) {
        return flashSaleMapper.selectList(new LambdaQueryWrapper<FlashSalePO>()
                        .le(FlashSalePO::getStartTime, now)
                        .ge(FlashSalePO::getEndTime, now)
                        .eq(FlashSalePO::getStatus, FlashSaleStatusEnum.ACTIVE.getCode()))
                .stream().map(FlashSaleRepositoryImpl::toDomain).toList();
    }

    @Override
    public boolean incrementSoldCountIfBelowStock(Long id, Integer version, int stock) {
        return flashSaleMapper.update(null, new LambdaUpdateWrapper<FlashSalePO>()
                .eq(FlashSalePO::getId, id)
                .eq(FlashSalePO::getVersion, version)
                .lt(FlashSalePO::getSoldCount, stock)
                .setSql("sold_count = sold_count + 1")) > 0;
    }

    @Override
    public void save(FlashSale flashSale) {
        FlashSalePO po = toPO(flashSale);
        flashSaleMapper.insert(po);
        flashSale.setId(po.getId());
    }

    @Override
    public void update(FlashSale flashSale) {
        flashSaleMapper.updateById(toPO(flashSale));
    }

    @Override
    public void deleteById(Long id) {
        flashSaleMapper.deleteById(id);
    }

    // ==================== PO <-> 聚合 ====================

    static FlashSale toDomain(FlashSalePO po) {
        if (po == null) {
            return null;
        }
        return FlashSale.builder()
                .id(po.getId())
                .spuId(po.getSpuId())
                .skuId(po.getSkuId())
                .flashPrice(po.getFlashPrice())
                .stock(po.getStock())
                .soldCount(po.getSoldCount())
                .startTime(po.getStartTime())
                .endTime(po.getEndTime())
                .status(po.getStatus())
                .version(po.getVersion())
                .createTime(po.getCreateTime())
                .build();
    }

    static FlashSalePO toPO(FlashSale flashSale) {
        FlashSalePO po = new FlashSalePO();
        po.setId(flashSale.getId());
        po.setSpuId(flashSale.getSpuId());
        po.setSkuId(flashSale.getSkuId());
        po.setFlashPrice(flashSale.getFlashPrice());
        po.setStock(flashSale.getStock());
        po.setSoldCount(flashSale.getSoldCount());
        po.setStartTime(flashSale.getStartTime());
        po.setEndTime(flashSale.getEndTime());
        po.setStatus(flashSale.getStatus());
        po.setVersion(flashSale.getVersion());
        po.setCreateTime(flashSale.getCreateTime());
        return po;
    }
}
