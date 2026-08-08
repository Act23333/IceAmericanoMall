package org.noLazy.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnClass(RestClient.class)
public class RestClientConfig {

    /**
     * 仅在响应式（WebFlux）环境中，且没有用户自定义 Builder 时，提供默认 Builder。
     * 在 Servlet 环境中，Spring Boot 的 RestClientAutoConfiguration 会自动注册，
     * 所以这里通过 @ConditionalOnMissingBean 避免冲突。
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder reactiveRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 如果要在 Gateway 中使用 RestClient 实例，可以一并提供。
     * 注意：在 WebFlux 中，阻塞式客户端并非最佳实践，但如有需要，可创建。
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}