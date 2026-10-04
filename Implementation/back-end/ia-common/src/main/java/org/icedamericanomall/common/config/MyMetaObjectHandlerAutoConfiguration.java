package org.icedamericanomall.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 审计字段填充器自动装配（ia-common 共享内核，V5.5 收口）—— 归并此前散布在
 * cart / item / logistics / merchant / pay / search / trade 七个服务里的
 * {@code MyMetaObjectHandler} 逐字副本。
 *
 * <p><b>装配守卫</b>：
 * <ul>
 *   <li>{@code @ConditionalOnClass(MetaObjectHandler.class)} —— ia-common 对
 *       {@code mybatis-plus-spring-boot3-starter} 的依赖是 <b>optional</b>，未引入
 *       MyBatis-Plus 的服务（如 reactive 的 gateway-service）类路径上没有该接口，
 *       本配置整体跳过（与 {@link MybatisConfig} / {@link DistributedLockConfig} 同一手法）。</li>
 *   <li>{@code @ConditionalOnMissingBean(MetaObjectHandler.class)} —— <b>存在用户自定义
 *       填充器时自动退避</b>：Spring Boot 保证用户 Bean 定义（含组件扫描得到的
 *       {@code @Component}）先于自动配置注册，故当 user-service 保有自己那份（多填
 *       {@code registerTime}）时，本 Bean 不会被创建，避免「同名 Bean 冲突」与
 *       「registerTime 丢失」两类问题。</li>
 * </ul>
 *
 * <p><b>注册方式</b>：{@code META-INF/spring/...AutoConfiguration.imports}（ia-common 的包不在
 * 各服务组件扫描路径内，{@code @Component} 扫描不到）。
 *
 * <p><b>行为影响面</b>：凡是「引入 MyBatis-Plus 且未自带填充器」的服务，从此获得
 * 与历史副本一致的 {@code createTime}/{@code updateTime} 自动填充语义 —— 这也是本次收口
 * 的目的：消除「有的服务填、有的不填」造成的审计字段口径漂移。
 */
@Configuration
@ConditionalOnClass(MetaObjectHandler.class)
public class MyMetaObjectHandlerAutoConfiguration {

    /**
     * 审计字段填充器 Bean。
     *
     * @return 通用审计字段填充器（{@code createTime} / {@code updateTime}）
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler auditFieldMetaObjectHandler() {
        return new AuditFieldMetaObjectHandler();
    }
}
