package org.icedamericanomall.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.codec.ErrorDecoder;
import org.icedamericanomall.common.constant.SecurityHeaders;
import org.icedamericanomall.common.filter.TraceIdFilter;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.fallback.AddressClientFallback;
import org.icedamericanomall.fallback.AfterSaleClientFallback;
import org.icedamericanomall.fallback.AuthContextClientFallback;
import org.icedamericanomall.fallback.BalanceClientFallback;
import org.icedamericanomall.fallback.CartClientFallback;
import org.icedamericanomall.fallback.CouponClientFallback;
import org.icedamericanomall.fallback.FreightTemplateClientFallback;
import org.icedamericanomall.fallback.ItemSearchClientFallback;
import org.icedamericanomall.fallback.KnowledgeVectorClientFallback;
import org.icedamericanomall.fallback.LogisticsClientFallback;
import org.icedamericanomall.fallback.NotificationClientFallback;
import org.icedamericanomall.fallback.OrderClientFallback;
import org.icedamericanomall.fallback.PointsClientFallback;
import org.icedamericanomall.fallback.SpuDetailClientFallback;
import org.icedamericanomall.fallback.RefundClientFallback;
import org.icedamericanomall.fallback.SearchClientFallback;
import org.icedamericanomall.fallback.SkuClientFallback;
import org.icedamericanomall.fallback.UserClientFallback;
import org.icedamericanomall.fallback.UserProfileClientFallback;
import org.icedamericanomall.fallback.VerificationClientFallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FallbackFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享 Feign 配置回归测试（V5.3）。
 *
 * <p>锁死两条不变量：
 * <ol>
 *   <li>19 个 Client 的降级工厂<b>全部</b>在配置类里显式登记 —— 不再依赖各服务
 *       {@code scanBasePackages} 是否包含 {@code org.icedamericanomall.fallback}；</li>
 *   <li>Feign 日志级别默认 {@code BASIC}（{@code FULL} 会把 PII 请求体写进日志），
 *       仅当显式配置 {@code feign.logging.level=FULL} 时才放开。</li>
 * </ol>
 */
@DisplayName("DefaultFeignConfig — 降级工厂全量登记 + 日志级别")
class DefaultFeignConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DefaultFeignConfig.class);

    @Test
    @DisplayName("注册 — 19 个 fallbackFactory 全部就绪（与 ia-api 的 fallback 包一一对应）")
    void shouldRegisterAllFallbackFactories() {
        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeansOfType(FallbackFactory.class)).hasSize(20);
            assertThat(context).hasSingleBean(AddressClientFallback.class);
            assertThat(context).hasSingleBean(AfterSaleClientFallback.class);
            assertThat(context).hasSingleBean(AuthContextClientFallback.class);
            assertThat(context).hasSingleBean(BalanceClientFallback.class);
            assertThat(context).hasSingleBean(CartClientFallback.class);
            assertThat(context).hasSingleBean(CouponClientFallback.class);
            assertThat(context).hasSingleBean(FreightTemplateClientFallback.class);
            assertThat(context).hasSingleBean(ItemSearchClientFallback.class);
            assertThat(context).hasSingleBean(KnowledgeVectorClientFallback.class);
            assertThat(context).hasSingleBean(LogisticsClientFallback.class);
            assertThat(context).hasSingleBean(NotificationClientFallback.class);
            assertThat(context).hasSingleBean(OrderClientFallback.class);
            assertThat(context).hasSingleBean(PointsClientFallback.class);
            assertThat(context).hasSingleBean(SpuDetailClientFallback.class);
            assertThat(context).hasSingleBean(RefundClientFallback.class);
            assertThat(context).hasSingleBean(SearchClientFallback.class);
            assertThat(context).hasSingleBean(SkuClientFallback.class);
            assertThat(context).hasSingleBean(UserClientFallback.class);
            assertThat(context).hasSingleBean(UserProfileClientFallback.class);
            assertThat(context).hasSingleBean(VerificationClientFallback.class);
        });
    }

    @Test
    @DisplayName("日志级别 — 未配置时为 BASIC（不落请求/响应体，避免 PII 进日志）")
    void shouldDefaultToBasicLogging() {
        runner.run(context -> assertThat(context.getBean(Logger.Level.class)).isEqualTo(Logger.Level.BASIC));
    }

    @Test
    @DisplayName("日志级别 — feign.logging.level=FULL 时显式放开（仅限 dev 排查）")
    void shouldUseFullLogging_whenExplicitlyConfigured() {
        runner.withPropertyValues("feign.logging.level=FULL")
                .run(context -> assertThat(context.getBean(Logger.Level.class)).isEqualTo(Logger.Level.FULL));
    }

    @Test
    @DisplayName("日志级别 — 非法配置值回落 BASIC（不因拼写错误泄露 body）")
    void shouldFallBackToBasic_whenLevelInvalid() {
        runner.withPropertyValues("feign.logging.level=VERBOSE")
                .run(context -> assertThat(context.getBean(Logger.Level.class)).isEqualTo(Logger.Level.BASIC));
    }

    /**
     * 批次 ⑤：错误解码器必须登记在<b>同一个</b>共享配置类里。
     *
     * <p><b>为什么用「恰一个」而不是「存在」断言</b>：{@code ErrorDecoder} 是客户端级全局配置，
     * 一个 Feign 上下文里出现两个实现会在装配期抛 {@code NoUniqueBeanDefinitionException}
     * （或更糟：非确定性选一个）。{@code FeignClientsConfiguration} 的默认实现带
     * {@code @ConditionalOnMissingBean}，本 Bean 先注册即可安全取代它 —— 这条断言把这个前提锁住。
     */
    @Test
    @DisplayName("错误解码器 — 恰好一个 ErrorDecoder，且为 UpstreamErrorDecoder（覆盖 Feign 默认实现）")
    void shouldRegisterSingleUpstreamErrorDecoder() {
        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(ErrorDecoder.class);
            assertThat(context.getBean(ErrorDecoder.class)).isInstanceOf(UpstreamErrorDecoder.class);
        });
    }

    // ==================== ① X-Trace-Id 透传（批次 ⑤b）====================

    /**
     * ★ 批次 ⑤b 的验收核心之一：Feign 出站必须带上本线程 MDC 里的 traceId。
     *
     * <p><b>锁死的缺陷（去掉注入那行即红）</b>：只注入 {@code X-User-Id} 时，下游的
     * {@code TraceIdFilter} 会为每跳<b>另生成 UUID</b> —— 于是下游 {@code /internal/**} 错误体里的
     * {@code traceId} 与调用方 MDC 里的<b>不是同一个值</b>，跨服务回捞日志直接失效
     * （{@code 09-constraints} §三·八·3 要求错误体足以定位问题）。
     */
    @Test
    @DisplayName("X-Trace-Id 透传 — MDC 有值时注入该头（值一字不改）")
    void shouldInjectTraceIdHeader_whenMdcHasValue() {
        MDC.put(TraceIdFilter.TRACE_ID, "trace-out-1");
        try {
            RequestTemplate template = applyInterceptor();

            assertThat(template.headers().get(SecurityHeaders.X_TRACE_ID))
                    .containsExactly("trace-out-1");
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    @Test
    @DisplayName("X-Trace-Id 透传 — MDC 无值（异步/定时任务）时不注入、不抛异常、不造假值")
    void shouldNotInjectTraceIdHeader_whenMdcAbsent() {
        MDC.remove(TraceIdFilter.TRACE_ID);

        RequestTemplate template = applyInterceptor(); // 不得抛异常

        assertThat(template.headers()).doesNotContainKey(SecurityHeaders.X_TRACE_ID);
    }

    @Test
    @DisplayName("X-Trace-Id 透传 — MDC 值为空白时视为缺失（不得把空串当作追踪号发给下游）")
    void shouldNotInjectBlankTraceId() {
        MDC.put(TraceIdFilter.TRACE_ID, "   ");
        try {
            RequestTemplate template = applyInterceptor();

            assertThat(template.headers()).doesNotContainKey(SecurityHeaders.X_TRACE_ID);
        } finally {
            MDC.remove(TraceIdFilter.TRACE_ID);
        }
    }

    /** 用配置类里<b>真实</b>注册的拦截器 Bean 处理一个空的请求模板。 */
    private static RequestTemplate applyInterceptor() {
        RequestInterceptor interceptor = new DefaultFeignConfig().requestInterceptor();
        RequestTemplate template = new RequestTemplate();
        template.method("GET");
        template.target("http://item-service/internal/item/spu/0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        interceptor.apply(template);
        return template;
    }
}
