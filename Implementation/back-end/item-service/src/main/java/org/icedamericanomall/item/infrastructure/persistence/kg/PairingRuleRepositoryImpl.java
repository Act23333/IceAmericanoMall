package org.icedamericanomall.item.infrastructure.persistence.kg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.item.domain.model.kg.PairingRule;
import org.icedamericanomall.item.domain.repository.PairingRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 类目搭配规则仓储实现 —— MyBatis-Plus 访问 {@code pairing_rule} 表，
 * PO ↔ 聚合转换内聚于此（内联 static toPO/toDomain）。
 */
@Repository
@RequiredArgsConstructor
public class PairingRuleRepositoryImpl implements PairingRuleRepository {

    private final PairingRuleMapper pairingRuleMapper;

    /** 治理分页（AD61）：三维可选过滤 + id 升序稳定排序（uk_src_dst 决定同键唯一，id 定全序）。 */
    @Override
    public IPage<PairingRule> page(Long srcCategoryId, Long dstCategoryId, Integer status,
                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<PairingRulePO> wrapper = new LambdaQueryWrapper<PairingRulePO>()
                .orderByAsc(PairingRulePO::getId);
        if (srcCategoryId != null) {
            wrapper.eq(PairingRulePO::getSrcCategoryId, srcCategoryId);
        }
        if (dstCategoryId != null) {
            wrapper.eq(PairingRulePO::getDstCategoryId, dstCategoryId);
        }
        if (status != null) {
            wrapper.eq(PairingRulePO::getStatus, status);
        }
        return pairingRuleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper)
                .convert(PairingRuleRepositoryImpl::toDomain);
    }

    @Override
    public PairingRule findById(Long id) {
        return toDomain(pairingRuleMapper.selectById(id));
    }

    @Override
    public PairingRule findBySrcAndDst(Long srcCategoryId, Long dstCategoryId) {
        return toDomain(pairingRuleMapper.selectOne(new LambdaQueryWrapper<PairingRulePO>()
                .eq(PairingRulePO::getSrcCategoryId, srcCategoryId)
                .eq(PairingRulePO::getDstCategoryId, dstCategoryId)));
    }

    @Override
    public PairingRule save(PairingRule rule) {
        PairingRulePO po = toPO(rule);
        pairingRuleMapper.insert(po);
        rule.setId(po.getId());
        return rule;
    }

    @Override
    public void update(PairingRule rule) {
        pairingRuleMapper.updateById(toPO(rule));
    }

    @Override
    public List<PairingRule> findAllBySrc(Long srcCategoryId) {
        return pairingRuleMapper.selectList(new LambdaQueryWrapper<PairingRulePO>()
                        .eq(PairingRulePO::getSrcCategoryId, srcCategoryId)
                        .orderByAsc(PairingRulePO::getDstCategoryId))
                .stream().map(PairingRuleRepositoryImpl::toDomain).toList();
    }

    @Override
    public List<PairingRule> findAll() {
        return pairingRuleMapper.selectList(new LambdaQueryWrapper<PairingRulePO>()
                        .orderByAsc(PairingRulePO::getId))
                .stream().map(PairingRuleRepositoryImpl::toDomain).toList();
    }

    // ==================== PO <-> 聚合 ====================

    static PairingRule toDomain(PairingRulePO po) {
        if (po == null) {
            return null;
        }
        return PairingRule.builder()
                .id(po.getId())
                .srcCategoryId(po.getSrcCategoryId())
                .dstCategoryId(po.getDstCategoryId())
                .weight(po.getWeight())
                .remark(po.getRemark())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    static PairingRulePO toPO(PairingRule rule) {
        PairingRulePO po = new PairingRulePO();
        po.setId(rule.getId());
        po.setSrcCategoryId(rule.getSrcCategoryId());
        po.setDstCategoryId(rule.getDstCategoryId());
        po.setWeight(rule.getWeight());
        po.setRemark(rule.getRemark());
        po.setStatus(rule.getStatus());
        return po;
    }
}
