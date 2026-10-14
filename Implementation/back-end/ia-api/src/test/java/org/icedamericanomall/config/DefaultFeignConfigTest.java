package org.icedamericanomall.config;

import feign.Logger;
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
import org.icedamericanomall.fallback.ProductDetailClientFallback;
import org.icedamericanomall.fallback.RefundClientFallback;
import org.icedamericanomall.fallback.SearchClientFallback;
import org.icedamericanomall.fallback.SkuClientFallback;
import org.icedamericanomall.fallback.UserClientFallback;
import org.icedamericanomall.fallback.UserProfileClientFallback;
import org.icedamericanomall.fallback.VerificationClientFallback;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
            assertThat(context).hasSingleBean(ProductDetailClientFallback.class);
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
}
