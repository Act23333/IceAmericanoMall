package org.icedamericanomall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.mapper.ProductViewLogMapper;
import org.icedamericanomall.service.HistoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 浏览足迹服务 — V5.0 京东标准
 *
 * MySQL product_view_log 持久化 + Redis 热缓存
 * 重复浏览同一商品 → UPDATE view_time
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final ProductViewLogMapper logMapper;
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void record(Long userId, Long productId) {
        logMapper.recordView(userId, productId);
        redisTemplate.delete("user:history:" + userId);
    }

    @Override
    public IPage<Map<String, Object>> page(Long userId, int pageNum, int size,
                                            String keyword, String sort) {
        StringBuilder sql = new StringBuilder(
                "SELECT v.id, v.product_id, v.view_time, p.name AS product_name, "
                + "p.main_image, p.brand, "
                + "(SELECT MIN(s.price) FROM sku s WHERE s.product_id=v.product_id AND s.status=1) AS price "
                + "FROM product_view_log v JOIN product p ON v.product_id=p.id "
                + "WHERE v.user_id=?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND p.name LIKE ?");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY v.view_time ").append("asc".equals(sort) ? "ASC" : "DESC");

        // 查总数
        String countSql = "SELECT COUNT(*) FROM product_view_log v WHERE v.user_id=?"
                + (keyword != null && !keyword.isBlank() ? " AND EXISTS(SELECT 1 FROM product p WHERE p.id=v.product_id AND p.name LIKE ?)" : "");
        List<Object> countParams = new ArrayList<>();
        countParams.add(userId);
        if (keyword != null && !keyword.isBlank()) countParams.add("%" + keyword + "%");
        long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        if (total == 0) {
            Page<Map<String, Object>> empty = new Page<>(pageNum, size);
            empty.setTotal(0);
            return empty;
        }

        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add((pageNum - 1) * size);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Page<Map<String, Object>> page = new Page<>(pageNum, size);
        page.setRecords(records);
        page.setTotal(total);
        page.setPages((int) Math.ceil((double) total / size));
        return page;
    }

    @Override
    public void deleteById(Long userId, Long logId) {
        jdbcTemplate.update("DELETE FROM product_view_log WHERE id=? AND user_id=?", logId, userId);
        redisTemplate.delete("user:history:" + userId);
    }

    @Override
    public void clear(Long userId) {
        jdbcTemplate.update("DELETE FROM product_view_log WHERE user_id=?", userId);
        redisTemplate.delete("user:history:" + userId);
    }
}
