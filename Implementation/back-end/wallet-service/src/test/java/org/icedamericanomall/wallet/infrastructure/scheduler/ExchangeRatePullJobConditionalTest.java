package org.icedamericanomall.wallet.infrastructure.scheduler;

import org.icedamericanomall.wallet.domain.repository.CurrencyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExchangeRatePullJob} 开关注册语义测试——
 * {@code currency.rate.pull.enabled} 缺省/false 时 Bean 不注册（调度永不启动），true 时注册。
 */
@DisplayName("汇率拉取 Job — 开关默认关闭不注册（调度语义）")
class ExchangeRatePullJobConditionalTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(CurrencyRepository.class, () -> Mockito.mock(CurrencyRepository.class))
            .withUserConfiguration(ExchangeRatePullJob.class);

    @Test
    @DisplayName("开关缺省 → 不注册 Job Bean（默认 false，定时拉取不启动）")
    void shouldNotRegister_whenSwitchAbsent() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ExchangeRatePullJob.class));
    }

    @Test
    @DisplayName("开关 = false → 不注册 Job Bean")
    void shouldNotRegister_whenSwitchOff() {
        runner.withPropertyValues("currency.rate.pull.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExchangeRatePullJob.class));
    }

    @Test
    @DisplayName("开关 = true → 注册 Job Bean")
    void shouldRegister_whenSwitchOn() {
        runner.withPropertyValues("currency.rate.pull.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ExchangeRatePullJob.class));
    }
}
