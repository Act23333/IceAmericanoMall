package org.icedamericanomall.ai.infrastructure.persistence.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeEntry;
import org.icedamericanomall.ai.domain.repository.MerchantKnowledgeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商家知识库仓储实现（基础设施层）—— MyBatis-Plus 落库，PO ↔ 领域对象互转内联。
 *
 * <p>状态过滤（排除软删）与 {@code id + sellerId} 双键限定均在此落地，
 * 应用层只面向领域对象。
 */
@Repository
@RequiredArgsConstructor
public class MerchantKnowledgeRepositoryImpl implements MerchantKnowledgeRepository {

    private final MerchantKnowledgeMapper knowledgeMapper;

    @Override
    public KnowledgeEntry save(KnowledgeEntry entry) {
        MerchantKnowledgePO po = toPO(entry);
        knowledgeMapper.insert(po);
        return toDomain(po);
    }

    @Override
    public KnowledgeEntry findByIdAndSeller(Long id, Long sellerId) {
        MerchantKnowledgePO po = knowledgeMapper.selectOne(new LambdaQueryWrapper<MerchantKnowledgePO>()
                .eq(MerchantKnowledgePO::getId, id)
                .eq(MerchantKnowledgePO::getSellerId, sellerId));
        return po == null ? null : toDomain(po);
    }

    @Override
    public List<KnowledgeEntry> findBySeller(Long sellerId, String category) {
        LambdaQueryWrapper<MerchantKnowledgePO> wrapper = new LambdaQueryWrapper<MerchantKnowledgePO>()
                .eq(MerchantKnowledgePO::getSellerId, sellerId)
                .ne(MerchantKnowledgePO::getStatus, KnowledgeEntry.STATUS_DELETED);
        if (category != null) {
            wrapper.eq(MerchantKnowledgePO::getCategory, category);
        }
        wrapper.orderByDesc(MerchantKnowledgePO::getCreateTime);
        return knowledgeMapper.selectList(wrapper)
                .stream()
                .map(MerchantKnowledgeRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public void update(KnowledgeEntry entry) {
        knowledgeMapper.updateById(toPO(entry));
    }

    /** 领域对象 → PO。 */
    private static MerchantKnowledgePO toPO(KnowledgeEntry entry) {
        MerchantKnowledgePO po = new MerchantKnowledgePO();
        po.setId(entry.getId());
        po.setSellerId(entry.getSellerId());
        po.setTitle(entry.getTitle());
        po.setContent(entry.getContent());
        po.setCategory(entry.getCategory());
        po.setEmbeddingId(entry.getEmbeddingId());
        po.setStatus(entry.getStatus());
        po.setCreateTime(entry.getCreateTime());
        po.setUpdateTime(entry.getUpdateTime());
        return po;
    }

    /** PO → 领域对象。 */
    private static KnowledgeEntry toDomain(MerchantKnowledgePO po) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(po.getId());
        entry.setSellerId(po.getSellerId());
        entry.setTitle(po.getTitle());
        entry.setContent(po.getContent());
        entry.setCategory(po.getCategory());
        entry.setEmbeddingId(po.getEmbeddingId());
        entry.setStatus(po.getStatus());
        entry.setCreateTime(po.getCreateTime());
        entry.setUpdateTime(po.getUpdateTime());
        return entry;
    }
}
