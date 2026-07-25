package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;

import java.util.List;
import java.util.Map;

/**
 * 商品搜索服务 — ES 全文检索 + DB LIKE 降级双实现。
 */
public interface SearchService {

    Page<ProductSearchVO> search(String keyword, Long categoryId, int page, int size);

    List<String> getHotKeywords(int limit);

    List<String> getSearchHistory(Long userId, int limit);

    /**
     * V2.5.2: 向量语义搜索 — kNN 检索（ES dense_vector）。
     *
     * @param embedding 查询向量（维度取决于 Embedding 模型）
     * @param size      返回 Top-K 数量
     * @return 向量检索结果
     */
    List<ProductSearchVO> vectorSearch(double[] embedding, int size);

    /** DDD 分层: ES 全量重建索引（Controller→Service→Mapper，Controller 不直接操作 Mapper） */
    Map<String, Object> reindex();
}
