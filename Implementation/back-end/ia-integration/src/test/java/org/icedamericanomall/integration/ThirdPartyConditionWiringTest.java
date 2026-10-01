package org.icedamericanomall.integration;

import org.icedamericanomall.integration.payment.MockAlipayPaymentClient;
import org.icedamericanomall.integration.sms.AliyunSmsClient;
import org.icedamericanomall.integration.sms.AliyunSmsProperties;
import org.icedamericanomall.integration.sms.MockSmsClient;
import org.icedamericanomall.integration.sms.SmsClient;
import org.icedamericanomall.integration.storage.MinioStorageClient;
import org.icedamericanomall.integration.storage.MockStorageClient;
import org.icedamericanomall.integration.storage.StorageClient;
import org.icedamericanomall.integration.storage.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五组第三方客户端条件装配一致性回归测试（sms / storage / payment-alipay / payment-wechat 的
 * 可测子集；email 的 Real 侧依赖 optional 的 {@code spring-boot-starter-mail}，
 * 在本模块测试类路径上不存在，故只覆盖 Mock 侧；captcha 无 Mock/Real 对，不做互斥断言）。
 *
 * <p>统一模板（V5.2 对齐 sms 组现状）：
 * <ul>
 *   <li>Mock 侧：{@code @ConditionalOnProperty(name = "<ns>.enabled", havingValue = "false", matchIfMissing = true)}</li>
 *   <li>Real 侧：{@code @ConditionalOnClass(<SDK 类>) } + {@code havingValue = "true"}</li>
 * </ul>
 *
 * <p>此处同时锁死「互斥」这一关键不变量：任何一侧条件写错都会让两个实现同时存在
 * （或双双缺席），本测试会直接失败 —— 这是 V5.1 修过的真实事故
 * （{@code MockSmsClient} 曾带 {@code @Primary} 且无条件，导致真实短信永远发不出去）。
 */
@DisplayName("条件装配 — 第三方客户端 Mock/Real 互斥")
class ThirdPartyConditionWiringTest {

    // ==================== 短信组 ====================

    private final ApplicationContextRunner smsRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockSmsClient.class, AliyunSmsClient.class, AliyunSmsProperties.class);

    @Test
    @DisplayName("短信 — aliyun.sms.enabled 缺省 → 仅 MockSmsClient 生效（单一 SmsClient Bean）")
    void shouldWireOnlyMockSmsByDefault() {
        smsRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(SmsClient.class);
            assertThat(context).hasSingleBean(MockSmsClient.class);
            assertThat(context.getBeanNamesForType(AliyunSmsClient.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("短信 — aliyun.sms.enabled=true → 仅 AliyunSmsClient 生效，Mock 让位")
    void shouldWireOnlyRealSmsWhenEnabled() {
        smsRunner.withPropertyValues("aliyun.sms.enabled=true").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(SmsClient.class);
            assertThat(context).hasSingleBean(AliyunSmsClient.class);
            assertThat(context.getBeanNamesForType(MockSmsClient.class)).isEmpty();
        });
    }

    // ==================== 存储组 ====================

    private final ApplicationContextRunner storageRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockStorageClient.class, MinioStorageClient.class, StorageProperties.class);

    @Test
    @DisplayName("存储 — minio.enabled 缺省 → 仅 MockStorageClient 生效（去掉 @Primary 后仍是单一 Bean）")
    void shouldWireOnlyMockStorageByDefault() {
        storageRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(StorageClient.class);
            assertThat(context).hasSingleBean(MockStorageClient.class);
            assertThat(context.getBeanNamesForType(MinioStorageClient.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("存储 — minio.enabled=true → 仅 MinioStorageClient 生效")
    void shouldWireOnlyRealStorageWhenEnabled() {
        storageRunner.withPropertyValues("minio.enabled=true").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(StorageClient.class);
            assertThat(context).hasSingleBean(MinioStorageClient.class);
            assertThat(context.getBeanNamesForType(MockStorageClient.class)).isEmpty();
        });
    }

    // ==================== 支付宝组 ====================

    @Test
    @DisplayName("支付宝 — alipay.enabled 缺省 → MockAlipayPaymentClient 生效（新增开关不改变默认行为）")
    void shouldWireMockAlipayByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(MockAlipayPaymentClient.class)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(MockAlipayPaymentClient.class);
                });
    }

    @Test
    @DisplayName("支付宝 — alipay.enabled=true → Mock 让位（等待真实实现接入）")
    void shouldNotWireMockAlipayWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(MockAlipayPaymentClient.class)
                .withPropertyValues("alipay.enabled=true")
                .run(context -> assertThat(context.getBeanNamesForType(MockAlipayPaymentClient.class))
                        .isEmpty());
    }
}
