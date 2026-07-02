package org.noLazy.common.config;

import io.seata.rm.datasource.DataSourceProxy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Seata AT 模式 DataSource 代理 — 拦截 SQL 生成 undo log。
 * 仅在 {@code seata.enabled=true} 时激活。
 *
 * <p>AT 模式原理：一阶段自动提交本地事务 + 记录 undo log；
 * 二阶段若需回滚，用 undo log 生成反向 SQL 自动补偿。</p>
 */
@Slf4j
@Configuration
@ConditionalOnClass(DataSourceProxy.class)
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true")
public class SeataDataSourceConfig {

    @PostConstruct
    public void init() {
        log.info("Seata AT 模式 DataSourceProxy 已激活");
    }

    @Bean
    @Primary
    public DataSource dataSourceProxy(DataSource dataSource) {
        return new DataSourceProxy(dataSource);
    }
}
