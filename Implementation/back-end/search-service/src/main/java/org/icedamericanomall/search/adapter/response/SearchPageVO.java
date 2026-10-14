package org.icedamericanomall.search.adapter.response;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 搜索分页响应信封（自持）—— 替代历史 MyBatis-Plus {@code Page} 信封（ADR-052 框架类型清理）。
 *
 * <p>JSON 字段与历史契约核心字段一致：{@code records / total / size / current / pages}
 * （MP Page 附带的 {@code orders/searchCount/optimizeCountSql} 等实现字段不进入契约）。
 *
 * @param <T> 记录类型
 */
@Data
public class SearchPageVO<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页记录 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 每页条数 */
    private long size;

    /** 当前页码（1 起） */
    private long current;

    /** 总页数 */
    private long pages;

    /** 装配信封（pages 由 total/size 推导）。 */
    public static <T> SearchPageVO<T> of(List<T> records, long total, int page, int size) {
        SearchPageVO<T> vo = new SearchPageVO<>();
        vo.setRecords(records);
        vo.setTotal(total);
        vo.setSize(size);
        vo.setCurrent(page);
        vo.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return vo;
    }
}
