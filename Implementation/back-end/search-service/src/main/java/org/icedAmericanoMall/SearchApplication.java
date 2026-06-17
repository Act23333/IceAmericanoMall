package org.icedAmericanoMall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * search-service — 商品搜索引擎（V1.1 正式启用 ElasticSearch）。
 * 当前阶段提供最小骨架，确保编译通过和服务注册。
 */
@SpringBootApplication
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
