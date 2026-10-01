package org.icedamericanomall.common.utils;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext ctx) throws BeansException {
        SpringContextHolder.context = ctx;
    }

    public static <T> T getBean(Class<T> clazz) {
        if (context == null) throw new IllegalStateException("ApplicationContext 未初始化");
        return context.getBean(clazz);
    }
}