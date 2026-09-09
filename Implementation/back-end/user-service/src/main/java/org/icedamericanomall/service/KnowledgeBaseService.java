package org.icedamericanomall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.entity.MerchantKnowledgeEntity;
import org.icedamericanomall.mapper.MerchantKnowledgeMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * V4.0 DDD: 商家知识库 Domain Service。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final MerchantKnowledgeMapper knowledgeMapper;

    public List<MerchantKnowledgeEntity> listBySeller(Long sellerId, String category) {
        var wrapper = new LambdaQueryWrapper<MerchantKnowledgeEntity>()
                .eq(MerchantKnowledgeEntity::getSellerId, sellerId)
                .ne(MerchantKnowledgeEntity::getStatus, 3);
        if (category != null) wrapper.eq(MerchantKnowledgeEntity::getCategory, category);
        wrapper.orderByDesc(MerchantKnowledgeEntity::getCreateTime);
        return knowledgeMapper.selectList(wrapper);
    }

    public void create(MerchantKnowledgeEntity entity) {
        entity.setStatus(2);
        knowledgeMapper.insert(entity);
    }

    public MerchantKnowledgeEntity getById(Long id, Long sellerId) {
        return knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeEntity>()
                        .eq(MerchantKnowledgeEntity::getId, id)
                        .eq(MerchantKnowledgeEntity::getSellerId, sellerId));
    }

    public void update(MerchantKnowledgeEntity entity) {
        knowledgeMapper.updateById(entity);
    }

    public void softDelete(Long id, Long sellerId) {
        MerchantKnowledgeEntity e = getById(id, sellerId);
        if (e != null) { e.setStatus(3); knowledgeMapper.updateById(e); }
    }
}
