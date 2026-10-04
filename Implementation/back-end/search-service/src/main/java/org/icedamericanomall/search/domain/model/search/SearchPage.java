package org.icedamericanomall.search.domain.model.search;

import java.util.List;

/**
 * 领域分页结果（框架无关）—— 替代 MyBatis-Plus {@code Page} 在领域契约中的位置。
 *
 * @param records 当前页记录
 * @param total  总记录数
 * @param page   页码（1 起）
 * @param size   每页条数
 */
public record SearchPage<T>(List<T> records, long total, int page, int size) {

    /** 空页。 */
    public static <T> SearchPage<T> empty(int page, int size) {
        return new SearchPage<>(List.of(), 0, page, size);
    }
}
