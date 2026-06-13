package org.noLazy.common.utils;

import cn.hutool.core.bean.BeanUtil;
import org.noLazy.common.convert.Convert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @ClassName: BeanUtils
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/21 16:14
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.utils
 */
public class BeanUtils extends BeanUtil {

    /**
     * 将原对象转换成目标对象，对于字段不匹配的字段可以使用转换器处理
     *
     * @param source  原对象
     * @param clazz   目标对象的class
     * @param convert 转换器
     * @param <R>     原对象类型
     * @param <T>     目标对象类型
     * @return 目标对象
     */
    public static <T, R> R copyBean(T source, Class<R> clazz, Convert<T, R> convert) {
        R target = copyBean(source, clazz);
        if (convert != null) {
            convert.convert(source, target);
        }
        return target;
    }
    /**
     * 将原对象转换成目标对象
     *
     * @param source  原对象
     * @param clazz   目标对象的class
     * @param <R>     原对象类型
     * @param <T>     目标对象类型
     * @return 目标对象
     */
    public static <T, R> R copyBean(T source, Class<R> clazz) {
        if (source == null) {
            return null;
        }
        return toBean(source,clazz);
    }

    public static <T, R> List<R> copyList(List<T> list, Class<R> clazz) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return copyToList(list, clazz);
    }

    public static <T, R> List<R> copyList(List<T> list, Class<R> clazz, Convert<T, R> convert) {
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        Function<T, R> mapper = r -> copyBean(r, clazz, convert);
        return list.stream().map(mapper).toList();
    }
}
