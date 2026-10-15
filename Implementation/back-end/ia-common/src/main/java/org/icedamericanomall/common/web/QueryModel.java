package org.icedamericanomall.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「查询参数对象」形参 —— 由 {@link JacksonQueryModelArgumentResolver} 用 Jackson 绑定。
 *
 * <p><b>解决什么</b>：Spring MVC 对裸 POJO 形参的属性绑定（{@code WebDataBinder} → {@code BeanWrapper}）
 * 只按 <b>Java 属性名</b>取查询串，且<b>不提供命名策略钩子</b>，因此
 * {@code spring.jackson.property-naming-strategy: SNAKE_CASE}（只作用于 JSON）、
 * {@code @RequestParam(name=...)}（只作用于逐个声明的参数）**都覆盖不到它** ——
 * 契约写 {@code category_id} 而 Java 字段是 {@code categoryId} 时会**静默绑定失败**（null，不报错）。
 *
 * <p><b>做法</b>：把这类 DTO 形参交给 Jackson 反序列化（见 {@link JacksonQueryModelArgumentResolver}），
 * 于是<b>命名策略只有一份</b>（既有的 {@code SNAKE_CASE}），报文体的规则与查询参数<b>完全一致</b>。
 *
 * <p><b>用法</b>（控制器签名保持 POJO，不摊开成参数列表）：
 * <pre>{@code
 * public Result<IPage<ProductVO>> page(@QueryModel ProductPageReq req) { ... }
 * }</pre>
 *
 * <p><b>只认 snake</b>：camelCase 写法会被 Jackson 视为未知属性而忽略（不迎合双写法）。
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryModel {
}
