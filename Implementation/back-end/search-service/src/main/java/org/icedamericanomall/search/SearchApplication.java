package org.icedamericanomall.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * search-service — 商品全文检索 (ES 7.17 + DB LIKE 降级)。
 * V1.1 正式启用 ElasticSearch，当前默认 DB 模式。
 *
 * <p><b>组件扫描范围</b>：基础包为 {@code org.icedamericanomall.search}（本服务）。
 * 共享模块的 Bean 由 ia-common 的 {@code AutoConfiguration.imports} 装配，无需扫描。
 * 不扫描 {@code org.icedamericanomall.config} —— 那是 ia-api 的 {@code DefaultFeignConfig}，
 * 属于 Feign 专用配置。
 *
 * <p><b>不启用 Feign</b>：本服务对外只提供检索能力，无任何跨服务调用
 * （商品/库存数据由 DbSearchServiceImpl 直查主库表），故不声明 {@code @EnableFeignClients}，
 * 也不扫描 ia-api 的 Feign 降级工厂包。
 */
@SpringBootApplication(scanBasePackages = "org.icedamericanomall.search")
@MapperScan("org.icedamericanomall.search.infrastructure.persistence")
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
