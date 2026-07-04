package org.icedAmericanoMall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * search-service — 商品全文检索 (ES 7.17 + DB LIKE 降级)。
 * V1.1 正式启用 ElasticSearch，当前默认 DB 模式。
 */
@SpringBootApplication
@MapperScan("org.icedAmericanoMall.mapper")
@EnableFeignClients(basePackages = "org.icedAmericanoMall.client")
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
