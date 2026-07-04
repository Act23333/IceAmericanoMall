package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;

import java.util.List;

/**
 * 商品搜索服务 — ES 全文检索 + DB LIKE 降级双实现。
 */
public interface SearchService {

    Page<ProductSearchVO> search(String keyword, Long categoryId, int page, int size);

    List<String> getHotKeywords(int limit);

    List<String> getSearchHistory(Long userId, int limit);
}
