package org.icedamericanomall.marketing.app.points;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.response.PointsBalanceVO;
import org.icedamericanomall.marketing.adapter.response.PointsLogVO;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;
import org.icedamericanomall.marketing.domain.repository.PointsLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分用例族编排（应用层）—— 单表流水（无跨聚合不变量）。
 *
 * <p>写为插入（{@code @Transactional} 原子）、读为按 userId 聚合（CQRS 直查持久化读模型）；
 * VO 装配收敛在本层（控制器不持有转换器）。
 */
@Component
@RequiredArgsConstructor
public class PointsManager {

    private final PointsLogRepository pointsLogRepository;
    private final MarketingConverter marketingConverter;

    /** 添加积分（签到/下单/任务），返回当前总余额。 */
    @Transactional(rollbackFor = Exception.class)
    public long addPoints(Long userId, int points, int type, String source) {
        pointsLogRepository.save(PointsLog.record(userId, points, type, source));
        return pointsLogRepository.sumByUserId(userId);
    }

    /** 积分总余额。 */
    public PointsBalanceVO getBalance(Long userId) {
        return marketingConverter.toPointsBalanceVO(userId, pointsLogRepository.sumByUserId(userId));
    }

    /** 积分明细分页（按创建时间倒序）。 */
    public IPage<PointsLogVO> getHistory(Long userId, int pageNum, int pageSize) {
        IPage<PointsLog> page = pointsLogRepository.pageByUserId(userId, pageNum, pageSize);
        return page.convert(marketingConverter::toPointsLogVO);
    }
}
