package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.icedAmericanoMall.domain.dto.ProductSearchResult;

import java.util.List;

/**
 * 商品搜索服务接口 — ES 全文检索 + DB 降级双实现。
 */
public interface SearchService {

    Page<ProductSearchResult> search(String keyword, Long categoryId, int page, int size);

    List<String> getHotKeywords();
}
