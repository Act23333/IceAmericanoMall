package org.icedamericanomall.user.app.history;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.user.adapter.response.HistoryVO;
import org.icedamericanomall.user.app.port.HistoryViewQuery;
import org.springframework.stereotype.Component;

/**
 * 浏览足迹（应用层）—— 记录 / 分页查询 / 删除 / 清空，
 * 持久化（联表读模型 + MySQL upsert）在 {@link HistoryViewQuery} 端口之后。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryManager {

    private final HistoryViewQuery historyViewQuery;

    /**
     * 记录浏览（同一商品重复浏览 → 更新 view_time）。
     *
     * <p><b>入参收业务键（ADR-061 §决策 2）</b>：{@code spuId} 为 SPU <b>业务键</b> 32-hex，
     * 经同库只读解析落 {@code spu_view_log.spu_id BIGINT}（A 案：内部外键保持技术键不动）。
     * 商品不存在/业务键非法 → 记 WARN 后直接返回（足迹是<b>可选埋点</b>，不因埋点失败
     * 影响浏览主流程——与收藏不同，此处刻意不抛异常）。
     */
    public void record(Long userId, String spuId) {
        Long technicalId = historyViewQuery.resolveSpuTechnicalId(spuId);
        if (technicalId == null) {
            log.warn("足迹埋点跳过：SPU 业务键无对应商品或格式非法: userId={}, spuId={}", userId, spuId);
            return;
        }
        historyViewQuery.record(userId, technicalId);
    }

    /** 分页查询足迹（商品详情 + 浏览时间，按时间排序）。 */
    public IPage<HistoryVO> page(Long userId, int pageNum, int size, String keyword, String sort) {
        return historyViewQuery.page(userId, pageNum, size, keyword, sort).convert(HistoryVO::from);
    }

    /** 删除单条足迹（仅限本人）。 */
    public void deleteById(Long userId, Long logId) {
        historyViewQuery.deleteById(userId, logId);
    }

    /** 清空本人全部足迹。 */
    public void clear(Long userId) {
        historyViewQuery.clear(userId);
    }
}
