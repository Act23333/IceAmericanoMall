package org.icedamericanomall.user.app.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.KnowledgeVO;
import org.icedamericanomall.user.infrastructure.persistence.knowledge.MerchantKnowledgeDO;
import org.icedamericanomall.user.infrastructure.persistence.knowledge.MerchantKnowledgeMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商家知识库（应用层）—— 发布 / 更新 / 软删除 / 列表（按商家隔离）。
 *
 * <p>状态语义：2=生效，3=已删除（软删）；列表过滤状态 3。
 */
@Component
@RequiredArgsConstructor
public class KnowledgeBaseManager {

    /** 新建条目状态：生效 */
    private static final int STATUS_ACTIVE = 2;

    /** 软删除状态 */
    private static final int STATUS_DELETED = 3;

    private final MerchantKnowledgeMapper knowledgeMapper;

    /** 商家条目列表（可按分类筛选）。 */
    public List<KnowledgeVO> listBySeller(Long sellerId, String category) {
        LambdaQueryWrapper<MerchantKnowledgeDO> wrapper = new LambdaQueryWrapper<MerchantKnowledgeDO>()
                .eq(MerchantKnowledgeDO::getSellerId, sellerId)
                .ne(MerchantKnowledgeDO::getStatus, STATUS_DELETED);
        if (category != null) {
            wrapper.eq(MerchantKnowledgeDO::getCategory, category);
        }
        wrapper.orderByDesc(MerchantKnowledgeDO::getCreateTime);
        return knowledgeMapper.selectList(wrapper).stream()
                .map(KnowledgeVO::from)
                .toList();
    }

    /** 新建条目（归属由登录上下文指定；空标题仍按原语义放行，由前端/后续迭代约束）。 */
    public KnowledgeVO create(Long sellerId, String title, String content, String category) {
        MerchantKnowledgeDO entity = new MerchantKnowledgeDO();
        entity.setSellerId(sellerId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setCategory(category);
        entity.setStatus(STATUS_ACTIVE);
        knowledgeMapper.insert(entity);
        return KnowledgeVO.from(entity);
    }

    /** 按 {id, sellerId} 查询条目（不存在返回 null）。 */
    public KnowledgeVO findById(Long id, Long sellerId) {
        MerchantKnowledgeDO entity = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeDO>()
                        .eq(MerchantKnowledgeDO::getId, id)
                        .eq(MerchantKnowledgeDO::getSellerId, sellerId));
        return entity == null ? null : KnowledgeVO.from(entity);
    }

    /**
     * null-safe 更新条目（仅限本人条目）。
     *
     * @return false = 条目不存在 / 非本人（控制器据此返回 404）
     */
    public boolean update(Long id, Long sellerId, String title, String content, String category) {
        MerchantKnowledgeDO entity = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeDO>()
                        .eq(MerchantKnowledgeDO::getId, id)
                        .eq(MerchantKnowledgeDO::getSellerId, sellerId));
        if (entity == null) {
            return false;
        }
        if (title != null) entity.setTitle(title);
        if (content != null) entity.setContent(content);
        if (category != null) entity.setCategory(category);
        knowledgeMapper.updateById(entity);
        return true;
    }

    /** 软删除条目（仅限本人条目）。 */
    public void softDelete(Long id, Long sellerId) {
        MerchantKnowledgeDO entity = knowledgeMapper.selectOne(
                new LambdaQueryWrapper<MerchantKnowledgeDO>()
                        .eq(MerchantKnowledgeDO::getId, id)
                        .eq(MerchantKnowledgeDO::getSellerId, sellerId));
        if (entity != null) {
            entity.setStatus(STATUS_DELETED);
            knowledgeMapper.updateById(entity);
        }
    }
}
