package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.PointsLogEntity;
import org.icedAmericanoMall.mapper.PointsLogMapper;
import org.icedAmericanoMall.service.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointsServiceImpl extends ServiceImpl<PointsLogMapper, PointsLogEntity> implements PointsService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addPoints(Long userId, int points, int type, String source) {
        PointsLogEntity log = new PointsLogEntity();
        log.setUserId(userId);
        log.setPoints(points);
        log.setType(type);
        log.setSource(source);
        save(log);
        return getBalance(userId);
    }

    @Override
    public long getBalance(Long userId) {
        return baseMapper.sumPointsByUserId(userId);
    }

    @Override
    public IPage<PointsLogEntity> getHistory(Long userId, int page, int size) {
        return lambdaQuery()
                .eq(PointsLogEntity::getUserId, userId)
                .orderByDesc(PointsLogEntity::getCreateTime)
                .page(new Page<>(page, size));
    }
}
