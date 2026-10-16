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

    /** 记录浏览（同一商品重复浏览 → 更新 view_time）。 */
    public void record(Long userId, Long spuId) {
        historyViewQuery.record(userId, spuId);
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
