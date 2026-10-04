package org.icedamericanomall.ai.infrastructure.config;

import org.icedamericanomall.ai.domain.service.ContentSafetyFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域层无状态服务的 Bean 注册。
 *
 * <p>domain 包保持零框架依赖（不得出现 Spring 注解），需要注入应用层的纯规则领域服务
 * 统一在本配置类以 {@code @Bean} 注册 —— Bean 装配属基础设施关注点，领域逻辑不受影响。
 */
@Configuration
public class AiDomainConfig {

    /**
     * AI 内容安全过滤器（纯规则领域服务，无状态）。
     *
     * @return 过滤器实例
     */
    @Bean
    public ContentSafetyFilter contentSafetyFilter() {
        return new ContentSafetyFilter();
    }
}
