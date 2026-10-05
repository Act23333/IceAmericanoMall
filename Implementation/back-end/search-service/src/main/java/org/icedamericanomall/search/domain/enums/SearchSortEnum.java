package org.icedamericanomall.search.domain.enums;

/**
 * 商品搜索排序方式（SE1）—— 领域枚举，框架无关。
 *
 * <p>契约取值见 05-API-Specification §4.14.10 SE1：{@code sort?(sales|price|newest|relevance)}；
 * {@link #RELEVANCE} 为缺省相关度排序（ES 默认打分 / DB 销量降序的历史现行为）。
 */
public enum SearchSortEnum {

    /** 销量降序。 */
    SALES("sales"),

    /** 价格升序（在售 SKU 最低价口径）。 */
    PRICE("price"),

    /** 上架时间最新在前（publishTime 降序）。 */
    NEWEST("newest"),

    /** 默认相关度（ES 默认打分 / DB 销量降序的现行为）。 */
    RELEVANCE("relevance");

    private final String value;

    SearchSortEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 宽松解析：null / 空白 / 未知取值一律回落 {@link #RELEVANCE}
     * （C 端排序参数非法不阻断搜索，与关键词为空即全量浏览的宽松口径一致）。
     *
     * @param value 契约排序取值（可空）
     * @return 对应枚举，绝不返回 null
     */
    public static SearchSortEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        for (SearchSortEnum sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        return RELEVANCE;
    }
}
