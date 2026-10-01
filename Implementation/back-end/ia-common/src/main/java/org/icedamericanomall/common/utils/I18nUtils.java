package org.icedamericanomall.common.utils;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * i18n 消息工具 — 在业务代码中获取多语言消息。
 * <pre>
 * Usage: i18nUtils.get("order.status.pending_payment")
 *         → zh_CN: 待付款  |  en_US: Pending Payment
 * </pre>
 *
 * <p>配套 {@code config/I18nConfig}（{@code MessageSource} + {@code AcceptHeaderLocaleResolver}），
 * 消息资源位于 {@code classpath:i18n/messages_zh_CN.properties} 与 {@code messages_en_US.properties}。
 *
 * <p>依赖注入改为<b>构造器注入</b>（V5.2）：原先是 {@code @Resource} 字段注入，违反
 * 「禁止字段注入」硬约束。注意本类未被登记进 {@code AutoConfiguration.imports}，
 * 需业务服务把 {@code org.icedamericanomall.common.utils} 纳入组件扫描才会生效
 * （与 {@code I18nConfig} 的登记方式不同，属 ADR-009「imports 或扫描二选一」的既有决策）。
 */
@Component
public class I18nUtils {

    private final MessageSource messageSource;

    public I18nUtils(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    public String get(String code) {
        return get(code, Locale.SIMPLIFIED_CHINESE);
    }
}
