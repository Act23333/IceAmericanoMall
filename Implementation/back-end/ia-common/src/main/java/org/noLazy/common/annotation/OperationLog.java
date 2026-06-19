package org.noLazy.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 — 标记在 Controller 方法上自动记录操作日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String value() default "";
    String action() default "";
}
