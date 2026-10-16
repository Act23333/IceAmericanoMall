package org.icedamericanomall.user.app.port;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/**
 * 浏览足迹查询（应用层端口，基础设施层用 JDBC 联表查询实现）。
 *
 * <p>足迹页返回"商品详情 + 浏览时间"的聚合读模型（含跨表 JOIN 与最低价子查询），
 * 属读路径（CQRS），不经过领域层。
 */
public interface HistoryViewQuery {

    /** 记录浏览（同一商品重复浏览 → 更新 view_time）。 */
    void record(Long userId, Long spuId);

    /**
     * 分页查询足迹（商品名搜索、时间排序）。
     *
     * @param sort {@code asc} 表示按浏览时间正序，其余按倒序
     */
    IPage<Map<String, Object>> page(Long userId, int pageNum, int size, String keyword, String sort);

    /** 删除单条足迹（仅限本人）。 */
    void deleteById(Long userId, Long logId);

    /** 清空本人全部足迹。 */
    void clear(Long userId);
}
