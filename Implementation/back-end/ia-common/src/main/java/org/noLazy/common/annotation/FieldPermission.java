package org.noLazy.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldPermission {
    // 声明哪个字段需要什么角色，如 {"avatar:VIP", "vipLevel:ADMIN"}
    String[] value();
}