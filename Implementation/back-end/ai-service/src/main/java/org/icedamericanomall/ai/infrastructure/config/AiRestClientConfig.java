package org.icedamericanomall.ai.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * ai-service 出站 {@link RestClient} 装配（servlet 环境修复）。
 *
 * <p><b>为什么需要本类</b>：{@code EmbeddingService}、{@code RerankerService} 直接注入
 * {@link RestClient}（实例，非 Builder）。而 ia-common 的 {@code RestClientConfig} 只在
 * <b>响应式（WebFlux）</b>环境下注册该 Bean（{@code @ConditionalOnWebApplication(REACTIVE)}），
 * 且它不是 auto-configuration（未登记到 {@code AutoConfiguration.imports}），
 * Spring Boot 自身的 {@code RestClientAutoConfiguration} 又只提供 {@code RestClient.Builder}。
 * 结果是：ai-service（servlet，且依赖 WebFlux 仅用于 SSE）在 {@code ai.enabled=true} 时
 * 因为「找不到 RestClient Bean」而启动失败。
 *
 * <p>修复方式：<b>在本服务内自建</b>（不改 ia-common，避免波及其它服务），
 * 复用容器中已有的 {@code RestClient.Builder}（Spring Boot 在 servlet 环境提供，
 * 若缺失则回退到 {@code RestClient.builder()}），并施加可配置超时（{@code ai.http.*}）。
 *
 * <p>超时之所以重要：Embedding/Reranker 是同步阻塞的外部模型调用，缺省无限等待会把
 * 客服线程池拖死；超时后由各服务自身降级（返回空向量 / 启发式重排序）。
 */
@Slf4j
@Configuration
public class AiRestClientConfig {

    /**
     * AI 能力共用的 RestClient。
     *
     * @param builderProvider 容器中的 Builder（servlet 环境由 Spring Boot 提供；缺失时自建）
     * @param aiProperties    超时配置来源（{@code ai.http.*}）
     * @return 已配置超时的 RestClient
     */
    @Bean
    @ConditionalOnMissingBean(RestClient.class)
    public RestClient aiRestClient(ObjectProvider<RestClient.Builder> builderProvider,
                                   AiProperties aiProperties) {
        RestClient.Builder builder = builderProvider.getIfAvailable(RestClient::builder);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) aiProperties.getHttp().getConnectTimeout().toMillis());
        factory.setReadTimeout((int) aiProperties.getHttp().getReadTimeout().toMillis());

        log.info("ai-service 出站 RestClient 已装配: connectTimeout={}, readTimeout={}",
                aiProperties.getHttp().getConnectTimeout(), aiProperties.getHttp().getReadTimeout());
        return builder.requestFactory(factory).build();
    }
}
