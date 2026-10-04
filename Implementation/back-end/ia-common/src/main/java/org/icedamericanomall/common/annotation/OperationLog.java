package org.icedamericanomall.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理操作审计标记 —— 标注在<b>管理端写操作</b>的 Controller 方法上，由
 * {@code org.icedamericanomall.common.aspect.OperationLogAspect} 输出<b>单行结构化 JSON 审计日志</b>。
 *
 * <p><b>管道定位（ADR-032）</b>：本注解是「审计双管道」中的<b>管道一</b>——日志管道。
 * 只发结构化 JSON 到专用 SLF4J logger（{@code AUDIT}），由 ELK / Logstash 采集与检索，
 * <b>不落库、不依赖任何服务 Bean</b>；因此它对全平台所有服务统一适用。
 * 需要「管理端可查询的落库审计」时走<b>管道二</b>（user 域在应用层显式写 {@code operation_log} 表）。
 *
 * <p><b>PII 安全默认</b>：默认<b>不记录任何参数值</b>（{@link #recordParams()} 默认 {@code false}），
 * 避免手机号、密文、令牌等随审计日志外泄。仅在参数确实只含标识/状态类字段时显式开启
 * {@code recordParams = true}，此时也只产出「参数类型 + id 类字段摘要」并截断（≤200 字符），
 * 绝不记录完整请求体。
 *
 * <p><b>用法</b>（module / action 请始终语义化填写，魔法值收敛为常量）：
 * <pre>{@code
 * @OperationLog(module = MODULE_RBAC, action = ACTION_CREATE_ROLE)
 * @PostMapping
 * public Result<RoleVO> createRole(@Valid @RequestBody RoleReq req) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 业务模块标识（如 {@code RBAC} / {@code TRADE_FINANCE} / {@code ITEM_HOME}）。
     *
     * <p>建议以常量填写，保证同一模块在审计日志中的取值稳定，便于 ELK 聚合检索。
     *
     * @return 模块标识，默认空串（不建议留空）
     */
    String module() default "";

    /**
     * 操作动作标识（如 {@code CREATE_ROLE} / {@code REVIEW_WITHDRAWAL}）。
     *
     * <p>建议使用「大写下划线」的动词短语，与模块组合后可唯一定位一个管理动作。
     *
     * @return 动作标识，默认空串（不建议留空）
     */
    String action() default "";

    /**
     * 人类可读的操作说明（可选），仅用于代码可读性与审计合规文档对照，不参与日志字段输出。
     *
     * @return 操作说明，默认空串
     */
    String description() default "";

    /**
     * 是否记录参数摘要。
     *
     * <p>默认 {@code false}：<b>任何参数值都不进入审计日志</b>（PII 安全默认）。
     * 置为 {@code true} 时，切面只输出「参数类型 + id 类字段摘要」（最多 200 字符），
     * 请求体中的业务字段（姓名、手机号、地址等）仍不会被记录。
     *
     * @return {@code true} 输出参数摘要；{@code false} 不输出（默认）
     */
    boolean recordParams() default false;
}
