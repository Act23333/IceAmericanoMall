package org.icedamericanomall.search.domain.model.search;

/**
 * 商品搜索查询（领域值对象）—— 对网关接口屏蔽 Web 层参数形态。
 *
 * @param keyword    关键词（可为空 = 全量浏览）
 * @param categoryId 分类过滤（可空）
 * @param page       页码（1 起）
 * @param size       每页条数
 */
public record SearchQuery(String keyword, Long categoryId, int page, int size) {

    /** 关键词是否有效（非空非空白）。 */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
