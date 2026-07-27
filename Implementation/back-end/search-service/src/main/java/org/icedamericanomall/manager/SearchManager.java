package org.icedamericanomall.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.domain.vo.ProductSearchVO;
import org.icedamericanomall.service.SearchService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V4.0 DDD: 搜索编排 Manager（Application层）。
 * 职责: ES/DB双实现路由编排、热搜词聚合、搜索历史管理。
 */
@Component
@RequiredArgsConstructor
public class SearchManager {

    private final SearchService searchService;

    public Page<ProductSearchVO> search(String keyword, Long categoryId, int page, int size) {
        return searchService.search(keyword, categoryId, page, size);
    }

    public List<String> getHotKeywords(int limit) {
        return searchService.getHotKeywords(limit);
    }

    public List<String> getSearchHistory(Long userId, int limit) {
        return searchService.getSearchHistory(userId, limit);
    }
}
