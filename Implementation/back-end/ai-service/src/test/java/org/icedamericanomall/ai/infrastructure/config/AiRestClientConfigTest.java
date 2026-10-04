package org.icedamericanomall.ai.infrastructure.config;

import org.icedamericanomall.ai.infrastructure.config.AiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiRestClientConfig} 回归测试 —— 锁死「servlet 环境下 RestClient Bean 一定可得」。
 *
 * <p>背景：{@code EmbeddingService}/{@code RerankerService} 直接注入 {@link RestClient} 实例，
 * 而 ia-common 的 {@code RestClientConfig} 只在 WebFlux 环境注册该 Bean，Spring Boot 自身
 * 只提供 {@code RestClient.Builder}。若本配置缺失，{@code ai.enabled=true} 时服务会因
 * 「找不到 RestClient Bean」启动失败（P0）。
 *
 * <p>两条路径都覆盖：〈Builder 存在（servlet 常规环境）〉与〈Builder 缺失（回退自建）〉。
 */
@DisplayName("AiRestClientConfig — RestClient Bean 装配")
class AiRestClientConfigTest {

    /** 模拟「依赖 RestClient 的 AI 能力 Bean」，验证注入链真正可用。 */
    @Configuration
    static class RestClientConsumerConfig {

        @Bean
        String consumerProbe(RestClient restClient) {
            return restClient == null ? "null" : "ok";
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiRestClientConfig.class, RestClientConsumerConfig.class)
            .withBean(AiProperties.class);

    @Test
    @DisplayName("装配 — Builder 缺失时回退 RestClient.builder()，RestClient 仍可得")
    void shouldProvideRestClient_whenBuilderMissing() {
        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context.getBean(String.class)).isEqualTo("ok");
        });
    }

    @Test
    @DisplayName("装配 — 容器已有 Builder 时复用它，RestClient 可得且唯一")
    void shouldProvideRestClient_whenBuilderPresent() {
        runner.withBean(RestClient.Builder.class, RestClient::builder)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(RestClient.class);
                });
    }
}
