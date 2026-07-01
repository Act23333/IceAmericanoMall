package org.noLazy.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * i18n 多语言配置 — 根据 Accept-Language 请求头自动切换语言。
 * 默认 zh_CN，支持 en_US。
 */
@Configuration
@ConditionalOnWebApplication(type = SERVLET)
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Bean
    public AcceptHeaderLocaleResolver customLocaleResolver() {
        return new AcceptHeaderLocaleResolver() {
            private final List<Locale> supported = Arrays.asList(
                    Locale.SIMPLIFIED_CHINESE, Locale.US);

            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String lang = request.getHeader("Accept-Language");
                if (lang == null || lang.isEmpty()) return Locale.SIMPLIFIED_CHINESE;
                Locale req = Locale.forLanguageTag(lang.split(",")[0]);
                return supported.contains(req) ? req : Locale.SIMPLIFIED_CHINESE;
            }
        };
    }
}
