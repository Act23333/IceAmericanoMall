package org.icedamericanomall.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * 注册 {@link JacksonQueryModelArgumentResolver} —— 经 {@code AutoConfiguration.imports} 生效，
 * 一处配置覆盖全部 15 个服务（与 ia-common 其他 Web 装配同一方式）。
 *
 * <p>仅 Servlet 应用生效（网关为 WebFlux，不涉及）。
 * 单独建类而**不改动 {@code WebConfig}**，以免影响其既有构造方式与测试。
 */
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
public class QueryModelWebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    public QueryModelWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(0, new JacksonQueryModelArgumentResolver(objectMapper));
    }
}
