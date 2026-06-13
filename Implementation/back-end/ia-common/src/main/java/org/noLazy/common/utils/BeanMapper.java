//package org.noLazy.common.utils;
//
//
//import cn.hutool.core.collection.CollectionUtil;
//import com.baomidou.mybatisplus.core.mapper.Mapper;
//import org.noLazy.common.convert.IMapper;
//
//import java.util.Collection;
//import java.util.Collections;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.Map;
//
//
///**
// * @ClassName: BeanMapper
// * @Description: 为了提升系统在高并发环境下的性能，我决定使用MapStruct作为domain的映射工具类
// * @Author: noLazy
// * @Date: 2026/3/24 23:21
// * @Version: 1.0.0
// * @ProjectName: IcedAmericanoMall
// * @Package: org.noLazy.common.utils
// */
//
//public final class BeanMapper {
//
//    // 存储映射器，Key 为源类型+目标类型
//    private static final Map<Key, IMapper<?, ?>> REGISTRY = new ConcurrentHashMap<>();
//
//    public static void register(Class<?> source, Class<?> target, IMapper<?, ?> mapper) {
//        REGISTRY.put(new Key(source, target), mapper);
//    }
//
//    @SuppressWarnings("unchecked") //未经检查的转换
//    public static <S, T> T map(S source, Class<T> targetClass) {
//        if (source == null) return null;
//        //source.getClass() 返回的是 Class<? extends S>
//        return findMapper((Class<S>) source.getClass(), targetClass).map(source);
//    }
//
//    public static <S, T> Collection<T> mapList(Collection<S> source, Class<T> targetClass) {
//        if (CollectionUtil.isEmpty(source)) return Collections.emptyList();
//        IMapper<S, T> mapper = findMapper(getElementClass(source), targetClass);
//        return mapper.mapList(source);
//    }
//
//    @SuppressWarnings("unchecked") //未经检查的转换
//    private static <S, T> IMapper<S, T> findMapper(Class<S> sourceClass, Class<T> targetClass) {
//        IMapper<S, T> mapper = (IMapper<S, T>) REGISTRY.get(new Key(sourceClass, targetClass));
//        if (mapper == null) {
//            throw new IllegalArgumentException("No mapper found for " + sourceClass.getSimpleName() + " -> " + targetClass.getSimpleName());
//        }
//        return mapper;
//    }
//
//    // 辅助方法：获取集合中元素的运行时类型（通过第一个非空元素推断）
//    @SuppressWarnings("unchecked")
//    private static <S> Class<S> getElementClass(Collection<S> collection) {
//        for (S item : collection) {
//            if (item != null) {
//                return (Class<S>) item.getClass();
//            }
//        }
//        // 集合全为 null 时无法推断类型
//        throw new IllegalArgumentException("Cannot infer source class from collection containing only nulls");
//    }
//    //作为registry注册表的key对象
//    private record Key(Class<?> source, Class<?> target) {
//    }
//}