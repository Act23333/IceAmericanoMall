//package org.noLazy.common.utils;
//
//import com.baomidou.mybatisplus.core.mapper.Mapper;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.core.ResolvableType;
//import org.springframework.stereotype.Component;
//
//@Component
//public class MapperRegistry implements BeanPostProcessor {
//
//    @Override
//    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//        // 判断是否实现了 Mapper 接口
//        if (bean instanceof Mapper<?, ?> mapper) {
//            // 解析 Mapper 接口的泛型参数
//            ResolvableType resolvableType = ResolvableType.forClass(bean.getClass()).as(Mapper.class);
//            Class<?> source = resolvableType.getGeneric(0).resolve();
//            Class<?> target = resolvableType.getGeneric(1).resolve();
//            if (source != null && target != null) {
//                // 自动注册到静态 MapBean
//                BeanMapper.register(source, target, mapper);
//            }
//        }
//        return bean;
//    }
//}