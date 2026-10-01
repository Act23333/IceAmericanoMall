package org.icedamericanomall.user.app.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.PointsLogVO;
import org.icedamericanomall.user.infrastructure.persistence.points.PointsLogDO;
import org.icedamericanomall.user.infrastructure.persistence.points.PointsLogMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分账本（应用层）—— 单表流水（无跨聚合不变量），写为插入、读为按 userId 聚合。
 *
 * <p>读路径（CQRS）直接使用持久化读模型，写入保持 {@code @Transactional} 原子（单表插入）。
 */
@Component
@RequiredArgsConstructor
public class PointsManager {

    private final PointsLogMapper pointsLogMapper;

    /** 添加积分（签到/下单/任务），返回当前总余额。 */
    @Transactional(rollbackFor = Exception.class)
    public long addPoints(Long userId, int points, int type, String source) {
        PointsLogDO log = new PointsLogDO();
        log.setUserId(userId);
        log.setPoints(points);
        log.setType(type);
        log.setSource(source);
        pointsLogMapper.insert(log);
        return getBalance(userId);
    }

    /** 查询积分总余额（数据库侧聚合）。 */
    public long getBalance(Long userId) {
        return pointsLogMapper.sumPointsByUserId(userId);
    }

    /** 积分明细分页。 */
    public IPage<PointsLogVO> getHistory(Long userId, int pageNum, int size) {
        IPage<PointsLogDO> result = pointsLogMapper.selectPage(new Page<>(pageNum, size),
                new LambdaQueryWrapper<PointsLogDO>()
                        .eq(PointsLogDO::getUserId, userId)
                        .orderByDesc(PointsLogDO::getCreateTime));
        return result.convert(PointsLogVO::from);
    }
}
