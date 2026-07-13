package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.mapper.FlashSaleMapper;
import org.icedAmericanoMall.service.FlashSaleService;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlashSaleServiceImpl extends ServiceImpl<FlashSaleMapper, FlashSaleEntity> implements FlashSaleService {

    @Override
    public List<FlashSaleEntity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery().le(FlashSaleEntity::getStartTime, now)
                .ge(FlashSaleEntity::getEndTime, now)
                .eq(FlashSaleEntity::getStatus, 2).list();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean buy(Long flashId) {
        FlashSaleEntity fs = getById(flashId);
        if (fs == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "秒杀活动不存在");
        if (fs.getStatus() != 2) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀未开始或已结束");
        if (fs.getSoldCount() >= fs.getStock()) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已售罄");
        // 原子条件扣减：UPDATE ... SET sold_count = sold_count + 1 WHERE id=? AND status=2 AND sold_count < stock
        // 以数据库单条 UPDATE 的行级锁保证并发下不超卖，影响行数为 0 即表示已售罄
        boolean ok = lambdaUpdate()
                .eq(FlashSaleEntity::getId, flashId)
                .eq(FlashSaleEntity::getStatus, 2)
                .apply("sold_count < stock")
                .setIncrBy(FlashSaleEntity::getSoldCount, 1)
                .update();
        if (!ok) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已售罄");
        return true;
    }
}
