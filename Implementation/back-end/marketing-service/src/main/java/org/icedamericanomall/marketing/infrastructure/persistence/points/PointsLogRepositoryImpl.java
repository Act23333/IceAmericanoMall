package org.icedamericanomall.marketing.infrastructure.persistence.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;
import org.icedamericanomall.marketing.domain.repository.PointsLogRepository;
import org.springframework.stereotype.Repository;

/**
 * 积分账本仓储实现 —— 单表流水：写为插入，读为按 userId 聚合/分页。
 */
@Repository
@RequiredArgsConstructor
public class PointsLogRepositoryImpl implements PointsLogRepository {

    private final PointsLogMapper pointsLogMapper;

    @Override
    public PointsLog save(PointsLog pointsLog) {
        PointsLogPO po = toPO(pointsLog);
        pointsLogMapper.insert(po);
        pointsLog.setId(po.getId());
        return pointsLog;
    }

    @Override
    public long sumByUserId(Long userId) {
        return pointsLogMapper.sumPointsByUserId(userId);
    }

    @Override
    public IPage<PointsLog> pageByUserId(Long userId, int pageNum, int pageSize) {
        return pointsLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PointsLogPO>()
                        .eq(PointsLogPO::getUserId, userId)
                        .orderByDesc(PointsLogPO::getCreateTime))
                .convert(PointsLogRepositoryImpl::toDomain);
    }

    // ==================== PO <-> 聚合 ====================

    static PointsLog toDomain(PointsLogPO po) {
        if (po == null) {
            return null;
        }
        return PointsLog.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .points(po.getPoints())
                .type(po.getType())
                .source(po.getSource())
                .createTime(po.getCreateTime())
                .build();
    }

    static PointsLogPO toPO(PointsLog pointsLog) {
        PointsLogPO po = new PointsLogPO();
        po.setId(pointsLog.getId());
        po.setUserId(pointsLog.getUserId());
        po.setPoints(pointsLog.getPoints());
        po.setType(pointsLog.getType());
        po.setSource(pointsLog.getSource());
        po.setCreateTime(pointsLog.getCreateTime());
        return po;
    }
}
