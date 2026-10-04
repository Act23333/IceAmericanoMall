package org.icedamericanomall.integration.payment;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 微信支付的装配判定（Real / Mock 互斥）—— 见 {@link WechatPaymentClient} 的装配说明。
 *
 * <p>判定规则（V5.3 起唯一口径）：
 * <ol>
 *   <li>{@code wechat.pay.merchant-id} 为<b>空或缺失</b> → 走 Mock；</li>
 *   <li>{@code wechat.pay.enabled} 显式为 {@code false} → 强制走 Mock（显式关闭开关）；</li>
 *   <li>否则（merchant-id 非空且未显式关闭）→ 走真实支付。</li>
 * </ol>
 *
 * <p>之所以需要一个显式的开关而不是只靠 {@code merchant-id}：pay-service 的默认 yml 里
 * {@code merchant-id: ${WECHAT_PAY_MERCHANT_ID:}} 在环境变量未设时解析为<b>空串</b>，
 * 而空串在 {@code @ConditionalOnProperty(name = ...)}（只判存在性）下会被判定为「已配置」，
 * 导致真实实现被装配、Mock 又不匹配 —— 干净环境启动即崩（空 merchantId +
 * 不存在的密钥路径）。此处改为读取属性值并判空，空串可靠地落到 Mock 分支。
 */
final class WechatPayConditions {

    /** 显式开关属性键：{@code true}=真实支付、{@code false}=强制 Mock、空=自动（按 merchant-id）。 */
    static final String ENABLED_PROPERTY = "wechat.pay.enabled";

    /** 关闭开关的字面值。 */
    private static final String FALSE_VALUE = "false";

    private WechatPayConditions() {
    }

    /**
     * 是否装配真实微信支付实现。
     *
     * @param environment 调用方环境
     * @return {@code merchant-id} 非空白且未被 {@code wechat.pay.enabled=false} 显式关闭时为 true
     */
    static boolean isRealPayEnabled(Environment environment) {
        String merchantId = environment.getProperty(WechatPayProperties.PREFIX + ".merchant-id");
        if (merchantId == null || merchantId.isBlank()) {
            return false;
        }
        String enabled = environment.getProperty(ENABLED_PROPERTY);
        return !FALSE_VALUE.equalsIgnoreCase(enabled == null ? "" : enabled.trim());
    }

    /**
     * 是否装配 Mock 实现（与真实实现严格互补，保证任何配置下都有且只有一个 PaymentClient）。
     *
     * @param environment 调用方环境
     * @return {@link #isRealPayEnabled(Environment)} 取反
     */
    static boolean isMockPayEnabled(Environment environment) {
        return !isRealPayEnabled(environment);
    }

    /** 真实实现的装配条件。 */
    static class Real implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isRealPayEnabled(context.getEnvironment());
        }
    }

    /** Mock 实现的装配条件。 */
    static class Mock implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return isMockPayEnabled(context.getEnvironment());
        }
    }
}
