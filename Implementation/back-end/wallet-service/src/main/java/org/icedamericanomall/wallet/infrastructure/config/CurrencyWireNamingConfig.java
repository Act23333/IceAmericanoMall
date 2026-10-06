package org.icedamericanomall.wallet.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.icedamericanomall.dto.CurrencyRateCO;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * F16 跨服务线格式钉名配置（I61）。
 *
 * <p><b>背景</b>：本服务 Jackson 全局策略为 {@code SNAKE_CASE}（application.yml），
 * {@code CurrencyRateCO.rateScaled} 会被序列化为 {@code rate_scaled}；而消费方 trade-service
 * 为默认 camelCase 解码，契约 §4.14.3b I61 与「Feign 降级哨兵 {@code rateScaled=-1}」均按
 * {@code rateScaled} 钉名——若不纠正，trade 侧字段静默为 null，快照永远跳过。
 *
 * <p><b>为何用 mixin</b>：{@code CurrencyRateCO} 位于 ia-api（共享契约，本任务不许改动），
 * 无法像 {@code BalanceRefundReq} 先例那样在字段上直接标 {@code @JsonProperty}；mixin 是
 * Jackson 官方的「外部类注解钉名」通道，显式 {@code @JsonProperty} 优先于全局命名策略，
 * 影响面仅此一个类。
 */
@Configuration
public class CurrencyWireNamingConfig {

    /**
     * 注册 {@link CurrencyRateCO} 的 camelCase 钉名 mixin（仅覆盖多词字段 {@code rateScaled}）。
     *
     * @return builder 定制器（与 ia-common JsonConfig 的定制器并存，均会生效）
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer currencyRateCOCamelNamingCustomizer() {
        return builder -> builder.mixIn(CurrencyRateCO.class, CurrencyRateCOCamelMixin.class);
    }

    /** {@code CurrencyRateCO} 的钉名混入：显式 {@code @JsonProperty} 优先于全局 SNAKE_CASE 策略。 */
    abstract static class CurrencyRateCOCamelMixin {

        /** 定点整数汇率（线格式钉名 {@code rateScaled}，契约 §4.14.3b I61）。 */
        @JsonProperty("rateScaled")
        private Long rateScaled;
    }
}
