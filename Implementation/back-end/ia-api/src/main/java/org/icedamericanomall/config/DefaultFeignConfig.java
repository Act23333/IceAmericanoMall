package org.icedamericanomall.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.icedamericanomall.fallback.UserClientFallback;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.context.annotation.Bean;

/**
 * 共享 Feign 全局配置（ia-api 提供给各调用方服务）。
 *
 * <p>使用方式：在服务的启动类上声明
 * {@code @EnableFeignClients(basePackages = "org.icedamericanomall.client", defaultConfiguration = DefaultFeignConfig.class)}；
 * 也可在单个 {@code @FeignClient(configuration = DefaultFeignConfig.class)} 上声明。
 *
 * <p>包路径约定：ia-api 只使用 {@code org.icedamericanomall.client / dto / fallback / config}
 * 四个规范包，<b>不得</b>出现与服务实现同名的子包（如 {@code org.icedamericanomall.auth.*}）——
 * 曾经因该配置类落在 {@code org.icedamericanomall.auth.config}，与 authorization-service 的
 * 基础包形成「拆分包（split package）」，导致组件扫描与 jar 边界混乱，V5.1 已迁回本包。
 *
 * <p>注意：本类<b>不加</b> {@code @Configuration}/{@code @Component}。ia-api 是被业务服务
 * 以 jar 方式引入的契约模块，其包不在业务服务的组件扫描范围内；配置类需由
 * {@code defaultConfiguration} / {@code configuration} 显式挂载到调用方的容器里。
 */
public class DefaultFeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * 透传当前登录用户标识：下游服务通过 {@code X-User-Id} 头重建 UserContext。
     * 无登录上下文（内部任务/定时任务）时不注入。
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            var userInfo = UserContext.getUser();
            if (userInfo != null) {
                requestTemplate.header("X-User-Id", String.valueOf(userInfo.userId()));
            }
        };
    }

    /**
     * 显式注册 UserClient 降级工厂（ia-api 的 {@code fallback} 包不在业务服务扫描范围内，
     * 若仅靠 {@code @Component} 会拿不到 Bean）。
     */
    @Bean
    public UserClientFallback userClientFallback() {
        return new UserClientFallback();
    }
}
