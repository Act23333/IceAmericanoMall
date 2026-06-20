package org.noLazy.common.utils;

import jakarta.annotation.Resource;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * i18n 消息工具 — 在业务代码中获取多语言消息。
 * <pre>
 * Usage: i18nUtils.get("order.status.pending_payment")
 *         → zh_CN: 待付款  |  en_US: Pending Payment
 * </pre>
 */
@Component
public class I18nUtils {

    @Resource
    private MessageSource messageSource;

    public String get(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    public String get(String code) {
        return get(code, Locale.SIMPLIFIED_CHINESE);
    }
}
