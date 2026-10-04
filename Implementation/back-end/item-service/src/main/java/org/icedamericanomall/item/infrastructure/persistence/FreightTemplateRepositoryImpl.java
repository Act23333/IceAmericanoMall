package org.icedamericanomall.item.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.enums.FreightTemplateStatusEnum;
import org.icedamericanomall.item.domain.model.freight.FreightTemplate;
import org.icedamericanomall.item.domain.repository.FreightTemplateRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 运费模板仓储实现 —— MyBatis-Plus 访问 {@code freight_template} 表，PO ↔ 聚合转换内聚于此。
 */
@Repository
@RequiredArgsConstructor
public class FreightTemplateRepositoryImpl implements FreightTemplateRepository {

    private final FreightTemplateMapper freightTemplateMapper;

    @Override
    public FreightTemplate findById(Long id) {
        FreightTemplatePO po = id == null ? null : freightTemplateMapper.selectById(id);
        return po == null ? null : toDomain(po);
    }

    @Override
    public boolean existsDuplicateName(Long sellerId, String templateName, Long excludeId) {
        Long count = freightTemplateMapper.selectCount(new LambdaQueryWrapper<FreightTemplatePO>()
                .eq(FreightTemplatePO::getSellerId, sellerId)
                .eq(FreightTemplatePO::getTemplateName, templateName)
                .ne(excludeId != null, FreightTemplatePO::getId, excludeId));
        return count != null && count > 0;
    }

    @Override
    public FreightTemplate save(FreightTemplate template) {
        FreightTemplatePO po = toPO(template);
        freightTemplateMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public void update(FreightTemplate template) {
        freightTemplateMapper.updateById(toPO(template));
    }

    @Override
    public void deleteById(Long id) {
        freightTemplateMapper.deleteById(id);
    }

    @Override
    public List<FreightTemplate> findBySeller(Long sellerId) {
        return freightTemplateMapper.selectList(new LambdaQueryWrapper<FreightTemplatePO>()
                        .eq(FreightTemplatePO::getSellerId, sellerId)
                        .orderByAsc(FreightTemplatePO::getId))
                .stream().map(FreightTemplateRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<FreightTemplate> findEnabledByIds(Long sellerId, List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return List.of();
        }
        return freightTemplateMapper.selectList(enabledSellerWrapper(sellerId)
                        .in(FreightTemplatePO::getId, templateIds)
                        .orderByAsc(FreightTemplatePO::getId))
                .stream().map(FreightTemplateRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<FreightTemplate> findEnabledBySeller(Long sellerId) {
        return freightTemplateMapper.selectList(enabledSellerWrapper(sellerId)
                        .orderByAsc(FreightTemplatePO::getId))
                .stream().map(FreightTemplateRepositoryImpl::toDomain).toList();
    }

    @Override
    public FreightTemplate findDefaultEnabled(Long sellerId) {
        List<FreightTemplatePO> first = freightTemplateMapper.selectList(enabledSellerWrapper(sellerId)
                .orderByAsc(FreightTemplatePO::getId)
                .last("LIMIT 1"));
        return first.isEmpty() ? null : toDomain(first.get(0));
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FreightTemplatePO> enabledSellerWrapper(Long sellerId) {
        return new LambdaQueryWrapper<FreightTemplatePO>()
                .eq(FreightTemplatePO::getSellerId, sellerId)
                .eq(FreightTemplatePO::getStatus, FreightTemplateStatusEnum.ENABLED.getCode());
    }

    private static FreightTemplate toDomain(FreightTemplatePO po) {
        return FreightTemplate.builder()
                .id(po.getId()).templateNo(po.getTemplateNo()).sellerId(po.getSellerId())
                .templateName(po.getTemplateName()).chargeMode(po.getChargeMode())
                .fee(po.getFee()).freeThreshold(po.getFreeThreshold()).status(po.getStatus())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build();
    }

    private static FreightTemplatePO toPO(FreightTemplate t) {
        FreightTemplatePO po = new FreightTemplatePO();
        po.setId(t.getId());
        po.setTemplateNo(t.getTemplateNo());
        po.setSellerId(t.getSellerId());
        po.setTemplateName(t.getTemplateName());
        po.setChargeMode(t.getChargeMode());
        po.setFee(t.getFee());
        po.setFreeThreshold(t.getFreeThreshold());
        po.setStatus(t.getStatus());
        return po;
    }
}
