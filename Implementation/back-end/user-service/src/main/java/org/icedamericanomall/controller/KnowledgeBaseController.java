package org.icedamericanomall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.domain.entity.MerchantKnowledgeEntity;
import org.icedamericanomall.mapper.MerchantKnowledgeMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * V3.4: 商家知识库管理 CRUD — FAQ/政策/产品手册上传，自动 RAG 索引。
 */
@Slf4j
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasRole('ROLE_SELLER')")
public class KnowledgeBaseController {

    private final MerchantKnowledgeMapper knowledgeMapper;

    @GetMapping("/knowledge")
    public Result<List<MerchantKnowledgeEntity>> list(
            @RequestParam(required = false) String category) {
        Long sellerId = UserContext.getUserId();
        var wrapper = new LambdaQueryWrapper<MerchantKnowledgeEntity>()
                .eq(MerchantKnowledgeEntity::getSellerId, sellerId)
                .ne(MerchantKnowledgeEntity::getStatus, 3);
        if (category != null) wrapper.eq(MerchantKnowledgeEntity::getCategory, category);
        wrapper.orderByDesc(MerchantKnowledgeEntity::getCreateTime);
        return Result.ok(knowledgeMapper.selectList(wrapper));
    }

    @PostMapping("/knowledge")
    public Result<MerchantKnowledgeEntity> create(@RequestBody MerchantKnowledgeEntity entity) {
        entity.setSellerId(UserContext.getUserId());
        entity.setStatus(2); // 已发布
        knowledgeMapper.insert(entity);
        // TODO: 自动分块 + Embedding + ES 索引 (DocumentChunker + EmbeddingService)
        return Result.ok(entity);
    }

    @PutMapping("/knowledge/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MerchantKnowledgeEntity req) {
        Long sellerId = UserContext.getUserId();
        MerchantKnowledgeEntity e = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeEntity>()
                        .eq(MerchantKnowledgeEntity::getId, id)
                        .eq(MerchantKnowledgeEntity::getSellerId, sellerId));
        if (e == null) return Result.error(404, "知识条目不存在");
        if (req.getTitle() != null) e.setTitle(req.getTitle());
        if (req.getContent() != null) e.setContent(req.getContent());
        if (req.getCategory() != null) e.setCategory(req.getCategory());
        knowledgeMapper.updateById(e);
        return Result.ok();
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long sellerId = UserContext.getUserId();
        MerchantKnowledgeEntity e = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeEntity>()
                        .eq(MerchantKnowledgeEntity::getId, id)
                        .eq(MerchantKnowledgeEntity::getSellerId, sellerId));
        if (e == null) return Result.error(404, "知识条目不存在");
        e.setStatus(3);
        knowledgeMapper.updateById(e);
        return Result.ok();
    }
}
