package org.icedamericanomall.ai.domain.port;

import org.icedamericanomall.ai.domain.model.knowledge.KnowledgeHit;
import org.icedamericanomall.ai.domain.model.product.ProductHit;

import java.util.List;

/**
 * 向量语义检索端口（领域层定义，基础设施层用 Feign 实现）。
 *
 * <p>两个方法对应 search-service 内部向量端点的两个索引分支：
 * {@link #vectorSearch} 走商品索引（RAG 的商品召回），
 * {@link #searchKnowledge} 走知识库 {@code knowledge_vectors} 索引（客服 RAG 的知识召回）。
 * 索引名与维度由向量索引所有方（search-service）自治，本层不出现索引名/维度常量。
 */
public interface VectorSearchPort {

    /**
     * 向量 kNN 检索商品。
     *
     * @param embedding 查询向量（为空时返回空列表，不发起远程调用）
     * @param size      返回条数
     * @param sellerId  商家ID（非空时按商家过滤，null 表示全站）
     * @return 命中商品列表（无结果为空列表，不返回 null）
     */
    List<ProductHit> vectorSearch(double[] embedding, int size, Long sellerId);

    /**
     * 向量 kNN 检索商家知识库 chunk（RAG 知识召回）。
     *
     * @param embedding 查询向量（为空时返回空列表，不发起远程调用）
     * @param size      返回条数（Top-K）
     * @param sellerId  商家ID（非空时按商家过滤；知识库检索应传，保证商家隔离）
     * @return 命中 chunk 列表（无结果为空列表，不返回 null）
     */
    List<KnowledgeHit> searchKnowledge(double[] embedding, int size, Long sellerId);
}
