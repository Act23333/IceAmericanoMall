package org.icedamericanomall.integration.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 微信支付条件装配回归测试（V5.3，P1）。
 *
 * <p>锁死不变量：
 * <ol>
 *   <li>任何配置下<b>恰好一个</b> {@code PaymentClient}（Real 与 Mock 严格互补）；</li>
 *   <li>pay-service 默认 yml 的形态 —— {@code merchant-id: ${WECHAT_PAY_MERCHANT_ID:}}
 *       在环境变量未设时是<b>空串</b> —— 必须落到 Mock（回归：此前空串被当成「已配置」，
 *       真实实现被装配且初始化必然失败，干净环境启动即崩）。</li>
 * </ol>
 *
 * <p>真实分支的 {@code @PostConstruct} 会读取 PEM 私钥文件（需要真实支付凭证），
 * 故此处用条件判定（{@link WechatPayConditions}）覆盖「merchant-id 非空 → Real」这一态，
 * 不在测试里把 Real 实例化。
 */
@DisplayName("微信支付 — 条件装配 Real/Mock 互斥")
class WechatPaymentClientWiringTest {

    private static final String MERCHANT_ID_PROPERTY = "wechat.pay.merchant-id";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WechatPaymentClient.class,
                    WechatPaymentClient.MockWechatPaymentClient.class,
                    WechatPayProperties.class);

    @Test
    @DisplayName("merchant-id 缺失 → 仅 MockWechatPaymentClient 生效")
    void shouldWireMockOnly_whenMerchantIdAbsent() {
        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(PaymentClient.class);
            assertThat(context).hasSingleBean(WechatPaymentClient.MockWechatPaymentClient.class);
            assertThat(context.getBeanNamesForType(WechatPaymentClient.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("merchant-id 为空串 → 仅 Mock（回归：此前既不满足 Real-ON、也不满足 Mock-ON，启动崩）")
    void shouldWireMockOnly_whenMerchantIdIsEmptyString() {
        runner.withPropertyValues(MERCHANT_ID_PROPERTY + "=").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(PaymentClient.class);
            assertThat(context).hasSingleBean(WechatPaymentClient.MockWechatPaymentClient.class);
            assertThat(context.getBeanNamesForType(WechatPaymentClient.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("merchant-id 为空串 + enabled=true → 仍落 Mock（开关不能造出缺 merchant-id 的 Real）")
    void shouldWireMockOnly_whenMerchantIdEmptyEvenIfEnabled() {
        runner.withPropertyValues(MERCHANT_ID_PROPERTY + "=", WechatPayConditions.ENABLED_PROPERTY + "=true")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(WechatPaymentClient.MockWechatPaymentClient.class);
                });
    }

    @Test
    @DisplayName("merchant-id 非空 + 未设 enabled → 判定为真实支付（兼容现有环境变量注入方式）")
    void shouldEnableRealPay_whenMerchantIdConfigured() {
        assertThat(WechatPayConditions.isRealPayEnabled(envWith(MERCHANT_ID_PROPERTY, "1900000109"))).isTrue();
        assertThat(WechatPayConditions.isMockPayEnabled(envWith(MERCHANT_ID_PROPERTY, "1900000109"))).isFalse();
    }

    @Test
    @DisplayName("wechat.pay.enabled=false → 即使 merchant-id 已配置也强制 Mock（显式 kill-switch）")
    void shouldForceMock_whenExplicitlyDisabled() {
        MockEnvironment environment = envWith(MERCHANT_ID_PROPERTY, "1900000109");
        environment.withProperty(WechatPayConditions.ENABLED_PROPERTY, "false");

        assertThat(WechatPayConditions.isRealPayEnabled(environment)).isFalse();
        assertThat(WechatPayConditions.isMockPayEnabled(environment)).isTrue();
    }

    @Test
    @DisplayName("merchant-id 存在但全为空白 → 判 Mock（不因空白串造出无意义的 Real）")
    void shouldDisableRealPay_whenMerchantIdBlank() {
        assertThat(WechatPayConditions.isRealPayEnabled(envWith(MERCHANT_ID_PROPERTY, "   "))).isFalse();
    }

    private MockEnvironment envWith(String key, String value) {
        return new MockEnvironment().withProperty(key, value);
    }
}
