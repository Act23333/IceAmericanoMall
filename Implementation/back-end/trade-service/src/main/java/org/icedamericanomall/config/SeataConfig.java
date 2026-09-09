package org.icedamericanomall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Seata 分布式事务配置 — 仅在 seata.enabled=true 时激活。
 * <p>
 * V2.0: Seata AT 模式用于支付回调（强一致），TCC 模式用于库存扣减，
 * Saga 模式用于下单全链路（替换手动 Saga 补偿）。
 * <p>
 * 降级方案：seata.enabled=false → 保留现有手动 Saga 补偿逻辑（OrderManager）。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true")
public class SeataConfig {
    // Seata 配置由 application.yml 的 seata.* 属性驱动
    // Spring Cloud Alibaba Seata 自动配置: https://github.com/alibaba/spring-cloud-alibaba
    // 需先部署 Seata Server (TC) 并配置 registry.conf + file.conf
}
