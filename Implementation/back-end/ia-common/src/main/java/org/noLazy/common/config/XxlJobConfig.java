package org.noLazy.common.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 执行器配置 — 仅在 xxl.job.enabled=true 且类路径有 XXL-Job 时激活。
 *
 * <pre>
 * Required configuration (application.yml):
 *   xxl.job:
 *     enabled: true
 *     admin-addresses: http://localhost:8088/xxl-job-admin
 *     executor:
 *       appname: trade-service
 *       port: 9999
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnClass(XxlJobSpringExecutor.class)
public class XxlJobConfig {

    @Bean
    @ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        log.info("XXL-Job executor initializing: appname={}, admin={}",
                properties.getExecutor().getAppname(), properties.getAdminAddresses());
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAppname(properties.getExecutor().getAppname());
        executor.setPort(properties.getExecutor().getPort());
        executor.setAccessToken(properties.getAccessToken());
        executor.setLogPath(properties.getExecutor().getLogPath());
        executor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());
        return executor;
    }

    @Bean
    @ConfigurationProperties(prefix = "xxl.job")
    public XxlJobProperties xxlJobProperties() {
        return new XxlJobProperties();
    }

    @lombok.Data
    public static class XxlJobProperties {
        private boolean enabled = false;
        private String adminAddresses = "http://localhost:8088/xxl-job-admin";
        private String accessToken;
        private Executor executor = new Executor();

        @lombok.Data
        public static class Executor {
            private String appname = "default-service";
            private int port = 9999;
            private String logPath = "/tmp/xxl-job";
            private int logRetentionDays = 30;
        }
    }
}
