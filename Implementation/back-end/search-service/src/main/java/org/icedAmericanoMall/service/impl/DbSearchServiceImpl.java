package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.domain.dto.ProductSearchResult;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 数据库搜索降级实现 — ES 未启用时的默认方案。
 * 通过 MySQL LIKE 做简单关键词搜索。
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DbSearchServiceImpl implements SearchService {

    private final JdbcTemplate jdbcTemplate;

    public DbSearchServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<ProductSearchResult> search(String keyword, Long categoryId, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id as productId, p.name, p.description, p.category_id as categoryId, "
                        + "s.price, p.main_image as image, p.sold_count as soldCount "
                        + "FROM product p "
                        + "LEFT JOIN sku s ON s.product_id = p.id AND s.status = 1 "
                        + "WHERE p.status = 1 ");
        StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM product p WHERE p.status = 1 ");

        if (keyword != null && !keyword.isBlank()) {
            String like = " AND (p.name LIKE '%" + keyword + "%' OR p.description LIKE '%" + keyword + "%') ";
            sql.append(like);
            countSql.append(like);
        }
        if (categoryId != null) {
            String cat = " AND p.category_id = " + categoryId + " ";
            sql.append(cat);
            countSql.append(cat);
        }

        sql.append(" GROUP BY p.id ORDER BY p.sold_count DESC LIMIT ").append(size)
                .append(" OFFSET ").append((page - 1) * size);

        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class);
        List<ProductSearchResult> records = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> {
                    ProductSearchResult r = new ProductSearchResult();
                    r.setProductId(rs.getLong("productId"));
                    r.setName(rs.getString("name"));
                    r.setDescription(rs.getString("description"));
                    r.setCategoryId(rs.getLong("categoryId"));
                    r.setPrice(rs.getInt("price"));
                    r.setImage(rs.getString("image"));
                    r.setSoldCount(rs.getInt("soldCount"));
                    return r;
                });

        Page<ProductSearchResult> resultPage = new Page<>(page, size);
        resultPage.setRecords(records);
        resultPage.setTotal(total != null ? total : 0);
        return resultPage;
    }

    @Override
    public List<String> getHotKeywords() {
        return Collections.emptyList();
    }
}
