package org.noLazy.common.domain;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.noLazy.common.utils.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @ClassName: PageDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/22 14:07
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.domain
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {
    //总记录数
    protected Long total;
    //总页数
    protected Long pages;
    //查询指定页的记录
    protected List<T> list;

    public static <T> PageDTO<T> empty(Long total, Long pages) {
        return new PageDTO<>(total, pages, Collections.emptyList());
    }

    public static <T> PageDTO<T> empty(Page<?> page) {
        return new PageDTO<>(page.getTotal(), page.getPages(), Collections.emptyList());
    }
    public static <T> PageDTO<T> of(Page<T> page) {
        if (Objects.isNull(page)) {
            return new PageDTO<>();
        }
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), page.getRecords());
    }
    public static <T, R> PageDTO<R> of(Page<T> page, Function<T, R> mapper) {
        if (Objects.isNull(page)) {
            return new PageDTO<>();
        }
        if (CollectionUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageDTO<>(page.getTotal(), page.getPages(), page.getRecords().stream().map(mapper).toList());
    }
    public static <T> PageDTO<T> of(Page<?> page, List<T> list) {
        return new PageDTO<>(page.getTotal(), page.getPages(), list);
    }
    public static <T, R> PageDTO<R> of(Page<T> page, Class<R> clazz) {
        return new PageDTO<>(page.getTotal(), page.getPages(), BeanUtils.copyList(page.getRecords(), clazz));
    }



}
