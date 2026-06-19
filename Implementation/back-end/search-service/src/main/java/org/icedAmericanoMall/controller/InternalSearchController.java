package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内部搜索管理接口 — 索引重建等运维操作。供 XXL-Job / Canal 调用。
 */
@Slf4j
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 全量重建商品索引 — 从 MySQL 拉取所有上架商品写入 ES。
     * 仅在 ES 启用时有效。
     */
    @PostMapping("/reindex")
    public String reindex() {
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
                "SELECT p.id as productId, p.name, p.description, p.category_id as categoryId, "
                        + "s.price, p.main_image as image, IFNULL(p.sold_count, 0) as soldCount "
                        + "FROM product p "
                        + "LEFT JOIN sku s ON s.product_id = p.id AND s.status = 1 "
                        + "WHERE p.status = 1 "
                        + "GROUP BY p.id");

        log.info("Reindexing {} products into ElasticSearch...", products.size());
        // ES reindex 将在 V1.1 ES 上线后启用
        return "Reindex triggered for " + products.size() + " products (ES not yet connected)";
    }
}
