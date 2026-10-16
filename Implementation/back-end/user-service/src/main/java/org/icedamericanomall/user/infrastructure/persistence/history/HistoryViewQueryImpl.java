package org.icedamericanomall.user.infrastructure.persistence.history;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.user.app.port.HistoryViewQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 浏览足迹查询实现（基础设施层）—— spu_view_log 联表商品读模型。
 *
 * <p>直接查库（读路径，CQRS）：商品名/主图/品牌 + 最低价子查询 + 浏览时间排序；
 * 游标/深分页优化属后续事项（当前保持与原实现一致的分页语义）。
 */
@Repository
@RequiredArgsConstructor
public class HistoryViewQueryImpl implements HistoryViewQuery {

    private final SpuViewLogMapper logMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Long resolveSpuTechnicalId(String spuBusinessId) {
        // BINARY(16) 列与 byte[] 参数比较（唯一索引 uk_spu_id），非法/空入参直接落「未命中」
        byte[] key = IdGenerator.toBytesOrNull(spuBusinessId);
        if (key == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT p.id FROM spu p WHERE p.spu_id = ? LIMIT 1", Long.class, key);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public void record(Long userId, Long spuTechnicalId) {
        logMapper.recordView(userId, spuTechnicalId);
    }

    @Override
    public IPage<Map<String, Object>> page(Long userId, int pageNum, int size, String keyword, String sort) {
        StringBuilder sql = new StringBuilder(
                "SELECT v.id, p.spu_id AS spu_business_id, v.view_time, p.name AS product_name, "
                        + "p.main_image, p.brand, "
                        + "(SELECT MIN(s.price) FROM sku s WHERE s.spu_id=v.spu_id AND s.status=1) AS price "
                        + "FROM spu_view_log v JOIN spu p ON v.spu_id=p.id "
                        + "WHERE v.user_id=?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword) {
            sql.append(" AND p.name LIKE ?");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY v.view_time ").append("asc".equals(sort) ? "ASC" : "DESC");

        String countSql = "SELECT COUNT(*) FROM spu_view_log v WHERE v.user_id=?"
                + (hasKeyword ? " AND EXISTS(SELECT 1 FROM spu p WHERE p.id=v.spu_id AND p.name LIKE ?)" : "");
        List<Object> countParams = new ArrayList<>();
        countParams.add(userId);
        if (hasKeyword) {
            countParams.add("%" + keyword + "%");
        }
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        long totalCount = total == null ? 0 : total;

        Page<Map<String, Object>> page = new Page<>(pageNum, size);
        page.setTotal(totalCount);
        if (totalCount == 0) {
            return page;
        }

        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add((pageNum - 1) * size);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        page.setRecords(records);
        page.setPages((long) Math.ceil((double) totalCount / size));
        return page;
    }

    @Override
    public void deleteById(Long userId, Long logId) {
        jdbcTemplate.update("DELETE FROM spu_view_log WHERE id=? AND user_id=?", logId, userId);
    }

    @Override
    public void clear(Long userId) {
        jdbcTemplate.update("DELETE FROM spu_view_log WHERE user_id=?", userId);
    }
}
