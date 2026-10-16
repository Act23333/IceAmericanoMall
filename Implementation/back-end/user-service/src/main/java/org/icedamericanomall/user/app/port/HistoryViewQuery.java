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

    /**
     * 记录浏览（同一商品重复浏览 → 更新 view_time）。
     *
     * @param spuTechnicalId 商品<b>技术键</b>（{@code spu.id}）—— 业务键解析由
     *                       {@link #resolveSpuTechnicalId(String)} 先行完成
     */
    void record(Long userId, Long spuTechnicalId);

    /**
     * SPU 业务键 → 技术键（同库只读解析，单条查询走 {@code spu.spu_id} 唯一索引）。
     *
     * <p>口径依据：契约 §1.8②（{@code spu_id} 查询参数按业务键消费，服务端负责解析）
     * + ADR-061 §决策 2；A 案下 {@code spu_view_log.spu_id} 等内部外键保持技术键不动，
     * 故解析等价于一次唯一索引查询。
     *
     * @param spuBusinessId SPU 业务键（32-hex）
     * @return 技术键；业务键格式非法或商品不存在时返回 {@code null}（语义由调用方决定）
     */
    Long resolveSpuTechnicalId(String spuBusinessId);

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
