package org.noLazy.common.annotation;


import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    @AliasFor(value = "value")
    String key();           // 限流 key 的 SpEL 表达式
    int limit() default 5;            // 次数
    long duration() default 60;       // 时间窗口（秒）
    TimeUnit unit() default TimeUnit.SECONDS;
}