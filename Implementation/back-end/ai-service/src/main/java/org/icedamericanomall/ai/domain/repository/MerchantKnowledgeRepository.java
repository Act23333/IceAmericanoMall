package org.icedamericanomall.ai.domain.repository;

import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeEntry;

import java.util.List;

/**
 * 商家知识库仓储端口（领域层）—— {@code merchant_knowledge_base} 聚合的持久化契约。
 *
 * <p>入参/出参均为领域对象 {@link KnowledgeEntry}；读写一律按业务键
 * （{@code id + sellerId} 双键限定，杜绝越权改他人条目），禁止按裸技术主键操作。
 */
public interface MerchantKnowledgeRepository {

    /**
     * 新增条目（技术主键由数据库自增并回填到返回对象）。
     *
     * @param entry 待落库的条目（id 忽略）
     * @return 落库后的条目（含回填的 id）
     */
    KnowledgeEntry save(KnowledgeEntry entry);

    /**
     * 按 {@code id + sellerId} 查询条目。
     *
     * @param id       条目ID
     * @param sellerId 归属商家ID
     * @return 条目；不存在或非本人条目返回 null
     */
    KnowledgeEntry findByIdAndSeller(Long id, Long sellerId);

    /**
     * 查询商家的生效条目列表（排除软删），按创建时间倒序。
     *
     * @param sellerId 商家ID
     * @param category 分类筛选（null 表示不筛选）
     * @return 条目列表
     */
    List<KnowledgeEntry> findBySeller(Long sellerId, String category);

    /**
     * 全量更新条目（按 id 定位，携带领域对象全字段）。
     *
     * @param entry 已定位的条目（须含 id）
     */
    void update(KnowledgeEntry entry);
}
